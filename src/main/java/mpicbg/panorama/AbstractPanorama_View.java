package mpicbg.panorama;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.plugin.PlugIn;
import ij.process.Blitter;
import ij.process.ImageProcessor;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.GeneralPath;

import mpicbg.models.NoninvertibleModelException;

abstract public class AbstractPanorama_View implements PlugIn, KeyListener, MouseWheelListener, MouseListener, MouseMotionListener
{
	static private enum NaviMode {
		PAN_TILT,
		PAN_ONLY,
		TILT_ONLY,
		ROLL_ONLY
	}

	final static private String NL = System.getProperty( "line.separator" );

	protected class GUI
	{
		final private ImageWindow window;
		final private Canvas canvas;

		final private ImageJ ij;

		/* backup */
		private KeyListener[] windowKeyListeners;
		private KeyListener[] canvasKeyListeners;
		private KeyListener[] ijKeyListeners;

		private MouseListener[] canvasMouseListeners;
		private MouseMotionListener[] canvasMouseMotionListeners;

		private MouseWheelListener[] windowMouseWheelListeners;

		GUI( final ImagePlus imp )
		{
			window = imp.getWindow();
			canvas = imp.getCanvas();

			ij = IJ.getInstance();
		}

		/**
		 * Close the window
		 */
		final public void close()
		{
			window.close();
		}

		/**
		 * Add new event handlers.
		 */
		protected void takeOverGui()
		{
			canvas.addKeyListener( AbstractPanorama_View.this );
			window.addKeyListener( AbstractPanorama_View.this );

			canvas.addMouseMotionListener( AbstractPanorama_View.this );

			canvas.addMouseListener( AbstractPanorama_View.this );

			ij.addKeyListener( AbstractPanorama_View.this );

			window.addMouseWheelListener( AbstractPanorama_View.this );
		}

		/**
		 * Backup old event handlers for restore.
		 */
		final void backupGui()
		{
			canvasKeyListeners = canvas.getKeyListeners();
			windowKeyListeners = window.getKeyListeners();
			ijKeyListeners = IJ.getInstance().getKeyListeners();
			canvasMouseListeners = canvas.getMouseListeners();
			canvasMouseMotionListeners = canvas.getMouseMotionListeners();
			windowMouseWheelListeners = window.getMouseWheelListeners();
			clearGui();
		}

		/**
		 * Restore the previously active Event handlers.
		 */
		final void restoreGui()
		{
			clearGui();
			for ( final KeyListener l : canvasKeyListeners )
				canvas.addKeyListener( l );
			for ( final KeyListener l : windowKeyListeners )
				window.addKeyListener( l );
			for ( final KeyListener l : ijKeyListeners )
				ij.addKeyListener( l );
			for ( final MouseListener l : canvasMouseListeners )
				canvas.addMouseListener( l );
			for ( final MouseMotionListener l : canvasMouseMotionListeners )
				canvas.addMouseMotionListener( l );
			for ( final MouseWheelListener l : windowMouseWheelListeners )
				window.addMouseWheelListener( l );
		}

		/**
		 * Remove both ours and the backed up event handlers.
		 */
		final void clearGui()
		{
			for ( final KeyListener l : canvasKeyListeners )
				canvas.removeKeyListener( l );
			for ( final KeyListener l : windowKeyListeners )
				window.removeKeyListener( l );
			for ( final KeyListener l : ijKeyListeners )
				ij.removeKeyListener( l );
			for ( final MouseListener l : canvasMouseListeners )
				canvas.removeMouseListener( l );
			for ( final MouseMotionListener l : canvasMouseMotionListeners )
				canvas.removeMouseMotionListener( l );
			for ( final MouseWheelListener l : windowMouseWheelListeners )
				window.removeMouseWheelListener( l );

			canvas.removeKeyListener( AbstractPanorama_View.this );
			window.removeKeyListener( AbstractPanorama_View.this );
			ij.removeKeyListener( AbstractPanorama_View.this );
			canvas.removeMouseListener( AbstractPanorama_View.this );
			canvas.removeMouseMotionListener( AbstractPanorama_View.this );
			window.removeMouseWheelListener( AbstractPanorama_View.this );
		}
	}

	abstract protected class Mapper
	{
		protected boolean interpolate = true;
		abstract public void map( final ImageProcessor target );
		final public void toggleInterpolation()
		{
			interpolate = !interpolate;
		}
	}

	abstract protected class AbstractMappingThread extends Thread
	{
		final protected ImagePlus impSource;
		final protected ImagePlus impTarget;
		final protected ImageProcessor target;
		final protected ImageProcessor temp;

		protected boolean visualize = true;
		protected boolean pleaseRepaint;
		protected boolean keepPainting;
		protected double dt = 1;

		public AbstractMappingThread(
				final ImagePlus impSource,
				final ImagePlus impTarget,
				final ImageProcessor target )
		{
			this.impSource = impSource;
			this.impTarget = impTarget;

			this.target = target;
			this.temp = target.createProcessor( target.getWidth(), target.getHeight() );
			temp.snapshot();
			this.setName( "MappingThread" );
		}

		abstract protected void map();

		@Override
		final public void run()
		{
			while ( !isInterrupted() )
			{
				final boolean b;
				synchronized ( this )
				{
					b = pleaseRepaint;
					pleaseRepaint = keepPainting;
				}
				if ( b )
				{
					final long t = System.currentTimeMillis();

					map();

					final Object targetPixels = target.getPixels();
					target.setPixels( temp.getPixels() );
					temp.setPixels( targetPixels );
					impTarget.updateAndDraw();

					if ( visualize )
						visualize( impSource, temp.getWidth(), temp.getHeight(), p );
					dt = ( System.currentTimeMillis() - t ) / 1000f;
				}
				synchronized ( this )
				{
					try
					{
						if ( !pleaseRepaint ) wait();
					}
					catch ( final InterruptedException e ){}
				}
			}
		}

		final public void repaint( @SuppressWarnings( "hiding" ) final boolean keepPainting )
		{
			synchronized ( this )
			{
				this.keepPainting = keepPainting;
				pleaseRepaint = true;
				notify();
			}
		}

		abstract public void toggleInterpolation();

		final public void toggleVisualization()
		{
			visualize = !visualize;
		}
	}

	protected ImagePlus imp;
	protected ImageProcessor ip;
	protected ImageProcessor ipSource;
	private GUI gui;

	static protected int width = 400;
	static protected int height = 300;
	static protected double minLambda = 0;
	static protected double minPhi = 0;
	static protected double hfov = 2 * Math.PI;
	static protected double vfov = Math.PI;

	final protected EquirectangularProjection p = new EquirectangularProjection();
	final static private double step = Math.PI / 180.0;

	protected double lambda;
	protected double phi;
	protected double rho;
	protected double dLambda = 0;
	protected double dPhi = 0;

	/* coordinates where mouse dragging started and the drag distance */
	protected int oX, oY, dX, dY;
	protected double oRho;

	private NaviMode naviMode = NaviMode.PAN_TILT;

	abstract protected boolean setup( final ImagePlus imp );

	private AbstractMappingThread painter;

	@Override
	public void run( final String arg )
    {
		imp = IJ.getImage();

		if ( imp == null )
		{
			IJ.error( "No image open." );
			return;
		}

		if ( !setup( imp ) )
			return;

		run( imp, width, height, minLambda, minPhi, hfov, vfov );
    }

	abstract protected AbstractMappingThread createPainter( final ImagePlus impViewer );

	protected GUI createGUI( final ImagePlus impViewer )
	{
		return new GUI( impViewer );
	}

	final public void run(
			final ImagePlus imp,
			final int width,
			final int height,
			final double minLambda,
			final double minPhi,
			final double hfov,
			final double vfov )
	{
		ip = imp.getProcessor().createProcessor( width, height );
		final ImagePlus impViewer = new ImagePlus( "Panorama View", ip );

		/* initialize projection */
		p.setMinLambda( minLambda );
		p.setMinPhi( minPhi );
		p.setLambdaPiScale( Math.PI / hfov * imp.getWidth() );
		p.setPhiPiScale( Math.PI / vfov * ( imp.getHeight() - 1 ) );
		p.setTargetWidth( ip.getWidth() );
		p.setTargetHeight( ip.getHeight() );
		p.setF( 0.5 );

		System.out.println( p.getLambdaPiScale() + " " + p.getPhiPiScale() );

		/* instantiate and run mapper and painter */
		painter = createPainter( impViewer );

		impViewer.show();

		gui = createGUI( impViewer );
		gui.backupGui();
		gui.takeOverGui();

		painter.start();
		update( false );
    }

	final static protected void prepareExtendedImage(
			final ImageProcessor source,
			final ImageProcessor target )
	{
		if ( target.getWidth() > source.getWidth() )
		{
			target.copyBits( source, source.getWidth(), 0, Blitter.COPY );
			if ( target.getHeight() > source.getHeight() )
				target.copyBits( source, source.getWidth(), 0, Blitter.COPY );
		}
		if ( target.getHeight() > source.getHeight() )
			target.copyBits( source, 0, 1, Blitter.COPY );
		target.copyBits( source, 0, 0, Blitter.COPY );
	}

	final private void update( final boolean keepPainting )
	{
		painter.repaint( keepPainting );
	}

	@Override
	public void keyPressed( final KeyEvent e )
	{
		if ( e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER )
		{
			painter.interrupt();
			imp.getCanvas().setDisplayList( null );
			gui.restoreGui();
			if ( e.getKeyCode() == KeyEvent.VK_ESCAPE )
				gui.close();
		}
		else if ( e.getKeyCode() == KeyEvent.VK_SHIFT )
		{
			dLambda *= 10;
			dPhi *= 10;
		}
		else if ( e.getKeyCode() == KeyEvent.VK_CONTROL )
		{
			dLambda /= 10;
			dPhi /= 10;
		}
		else
		{
			final double v = keyModfiedSpeed( e.getModifiersEx() );
			if ( e.getKeyCode() == KeyEvent.VK_LEFT )
			{
				lambda -= v * step;
				update( false );
			}
			else if ( e.getKeyCode() == KeyEvent.VK_RIGHT )
			{
				lambda += v * step;
				update( false );
			}
			else if ( e.getKeyCode() == KeyEvent.VK_UP )
			{
				phi -= v * step;
				update( false );
			}
			else if ( e.getKeyCode() == KeyEvent.VK_DOWN )
			{
				phi += v * step;
				update( false );
			}
			else if ( e.getKeyCode() == KeyEvent.VK_PLUS || e.getKeyCode() == KeyEvent.VK_EQUALS )
			{
				p.setF( p.getF() * ( 1 + 0.1f * v ) );
				update( false );
			}
			else if ( e.getKeyCode() == KeyEvent.VK_MINUS )
			{
				p.setF( p.getF() / ( 1 + 0.1f * v ) );
				update( false );
			}
//			else if ( e.getKeyCode() == KeyEvent.VK_SPACE )
//			{
//				renderCubeFaces( hfov, vfov );
//				update( false );
//			}
			else if ( e.getKeyCode() == KeyEvent.VK_I )
			{
				painter.toggleInterpolation();
				update( false );
			}
			else if ( e.getKeyCode() == KeyEvent.VK_V )
			{
				painter.toggleVisualization();
				imp.getCanvas().setDisplayList( null );
				update( false );
			}
			else if ( e.getKeyCode() == KeyEvent.VK_A )
			{
				naviMode = NaviMode.PAN_TILT;
				dLambda = dPhi = 0;
				update( false );
			}
			else if ( e.getKeyCode() == KeyEvent.VK_P )
			{
				naviMode = NaviMode.PAN_ONLY;
				dLambda = dPhi = 0;
				update( false );
			}
			else if ( e.getKeyCode() == KeyEvent.VK_T )
			{
				naviMode = NaviMode.TILT_ONLY;
				dLambda = dPhi = 0;
				update( false );
			}
			else if ( e.getKeyCode() == KeyEvent.VK_R )
			{
				naviMode = NaviMode.ROLL_ONLY;
				dLambda = dPhi = 0;
				update( false );
			}
			else if ( e.getKeyCode() == KeyEvent.VK_F1 )
			{
				IJ.showMessage(
						"Interactive Panorama Viewer",
						"Mouse control:" + NL + " " + NL +
						"Pan and tilt the panorama by dragging the image in the canvas and" + NL +
						"zoom in and out using the mouse-wheel." + NL + " " + NL +
						"Key control:" + NL + " " + NL +
						"CURSOR LEFT - Pan left." + NL +
						"CURSOR RIGHT - Pan right." + NL +
						"CURSOR UP - Tilt up." + NL +
						"CURSOR DOWN - Tilt down." + NL +
						"SHIFT - Move 10x faster." + NL +
						"CTRL - Move browse 10x slower." + NL +
						"ENTER/ESC - Leave interactive mode." + NL +
						"I - Toggle interpolation." + NL +
						"V - Toggle FOV visualization." + NL +
						"R - Roll-mode (roll via mouse drag)." + NL +
						"P - Pan/Tilt-mode (pan/tilt via mouse drag)." );
			}
		}
	}

	final private double keyModfiedSpeed( final int modifiers )
	{
		if ( ( modifiers & KeyEvent.SHIFT_DOWN_MASK ) != 0 )
			return 10;
		else if ( ( modifiers & KeyEvent.CTRL_DOWN_MASK ) != 0 )
			return 0.1f;
		else
			return 1;
	}

	@Override
	public void keyReleased( final KeyEvent e )
	{
		if ( e.getKeyCode() == KeyEvent.VK_SHIFT )
		{
			dLambda /= 10;
			dPhi /= 10;
		}
		else if ( e.getKeyCode() == KeyEvent.VK_CONTROL )
		{
			dLambda *= 10;
			dPhi *= 10;
		}
	}
	@Override
	public void keyTyped( final KeyEvent e ){}

	@Override
	public void mouseWheelMoved( final MouseWheelEvent e )
	{
		final double v = keyModfiedSpeed( e.getModifiersEx() );
		final int s = e.getWheelRotation();
		p.setF( p.getF() * ( 1 - 0.05f * s * v ) );
		update( false );
	}

	@Override
	public void mouseDragged( final MouseEvent e )
	{
		dX = oX - e.getX();
		dY = oY - e.getY();
		if ( naviMode == NaviMode.ROLL_ONLY )
		{
			final double d = Math.sqrt( dX * dX + dY * dY );
			rho = oRho + Math.atan2( dY / d, dX / d );
		}
		else
		{
			final double v = 0.1 * step * keyModfiedSpeed( e.getModifiersEx() );
			dLambda = v * dX;
			dPhi = -v * dY;
		}
		update( true );
	}

	@Override
	public void mouseMoved( final MouseEvent e ){}
	@Override
	public void mouseClicked( final MouseEvent e ){}
	@Override
	public void mouseEntered( final MouseEvent e ){}
	@Override
	public void mouseExited( final MouseEvent e ){}
	@Override
	public void mouseReleased( final MouseEvent e )
	{
		dLambda = dPhi = 0;
		oRho = rho;
		update( false );
	}
	@Override
	public void mousePressed( final MouseEvent e )
	{
		if ( naviMode == NaviMode.ROLL_ONLY )
		{
			oX = width / 2;
			oY = height / 2;
			dX = oX - e.getX();
			dY = oY - e.getY();
			final double d = Math.sqrt( dX * dX + dY * dY );
			oRho -= Math.atan2( dY / d, dX / d );
		}
		else
		{
			oX = e.getX();
			oY = e.getY();
		}
	}


	final static protected void visualize(
			final ImagePlus imp,
			final int w,
			final int h,
			final EquirectangularProjection p )
	{
		try
		{
			final double maxD = imp.getWidth() / 2;
			final GeneralPath gp = new GeneralPath();
			final double[] l = new double[]{ 0, 0 };
			p.applyInverseInPlace( l );
			double x0 = l[ 0 ];
			gp.moveTo( l[ 0 ], l[ 1 ] );
			for ( int x = 1; x < w; ++x )
			{
				l[ 0 ] = x;
				l[ 1 ] = 0;
				p.applyInverseInPlace( l );
				final double dx = l[ 0 ] - x0;
				if ( dx > maxD )
				{
					gp.lineTo( l[ 0 ] - imp.getWidth(), l[ 1 ] );
					gp.moveTo( l[ 0 ], l[ 1 ] );
				}
				else if ( dx < -maxD )
				{
					gp.lineTo( l[ 0 ] + imp.getWidth(), l[ 1 ] );
					gp.moveTo( l[ 0 ], l[ 1 ] );
				}
				else
					gp.lineTo( l[ 0 ], l[ 1 ] );
				x0 = l[ 0 ];
			}
			for ( int y = 1; y < h; ++y )
			{
				l[ 0 ] = w - 1;
				l[ 1 ] = y;
				p.applyInverseInPlace( l );
				final double dx = l[ 0 ] - x0;
				if ( dx > maxD )
				{
					gp.lineTo( l[ 0 ] - imp.getWidth(), l[ 1 ] );
					gp.moveTo( l[ 0 ], l[ 1 ] );
				}
				else if ( dx < -maxD )
				{
					gp.lineTo( l[ 0 ] + imp.getWidth(), l[ 1 ] );
					gp.moveTo( l[ 0 ], l[ 1 ] );
				}
				else
					gp.lineTo( l[ 0 ], l[ 1 ] );
				x0 = l[ 0 ];
			}
			for ( int x = w - 2; x >= 0; --x )
			{
				l[ 0 ] = x;
				l[ 1 ] = h - 1;
				p.applyInverseInPlace( l );
				final double dx = l[ 0 ] - x0;
				if ( dx > maxD )
				{
					gp.lineTo( l[ 0 ] - imp.getWidth(), l[ 1 ] );
					gp.moveTo( l[ 0 ], l[ 1 ] );
				}
				else if ( dx < -maxD )
				{
					gp.lineTo( l[ 0 ] + imp.getWidth(), l[ 1 ] );
					gp.moveTo( l[ 0 ], l[ 1 ] );
				}
				else
					gp.lineTo( l[ 0 ], l[ 1 ] );
				x0 = l[ 0 ];
			}
			for ( int y = h - 2; y >= 0; --y )
			{
				l[ 0 ] = 0;
				l[ 1 ] = y;
				p.applyInverseInPlace( l );
				final double dx = l[ 0 ] - x0;
				if ( dx > maxD )
				{
					gp.lineTo( l[ 0 ] - imp.getWidth(), l[ 1 ] );
					gp.moveTo( l[ 0 ], l[ 1 ] );
				}
				else if ( dx < -maxD )
				{
					gp.lineTo( l[ 0 ] + imp.getWidth(), l[ 1 ] );
					gp.moveTo( l[ 0 ], l[ 1 ] );
				}
				else
					gp.lineTo( l[ 0 ], l[ 1 ] );
				x0 = l[ 0 ];
			}
			imp.getCanvas().setDisplayList( gp, Color.YELLOW, null );
			imp.updateAndDraw();
		}
		catch ( final NoninvertibleModelException e ){}
	}
}
