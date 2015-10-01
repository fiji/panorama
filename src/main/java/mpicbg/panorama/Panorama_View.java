package mpicbg.panorama;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;
import mpicbg.ij.InverseTransformMapping;

public class Panorama_View extends AbstractPanorama_View
{
	final private class CubeFaceMapper
	{
		protected boolean interpolate = true;
		final private HomogeneousMapping< RectlinearCamera > mapping = new HomogeneousMapping< RectlinearCamera >( new RectlinearCamera() );
		final private PanoramaCamera< ? > camera;
		final private RectlinearCamera front;

		public CubeFaceMapper(
				final PanoramaCamera< ? > camera )
		{
			this.camera = camera;
			this.front = new RectlinearCamera();
			front.setCamera( camera );

			/* cubefaces have a width and height of +1px each for off-range interpolation */
			front.setSourceWidth( frontSource.getWidth() - 1 );
			front.setSourceHeight( frontSource.getHeight() - 1 );
		}

		final public void toggleInterpolation()
		{
			interpolate = !interpolate;
		}

		final public void map( final ImageProcessor target )
		{
			front.setTargetWidth( target.getWidth() );
			front.setTargetHeight( target.getHeight() );

			front.setCamera( camera );
			final RectlinearCamera face = front.clone();
			face.resetOrientation();
			target.reset();
			if ( interpolate ) /* TODO This is stupid---the mapping should have the interpolation method as a state */
			{
				mapping.getTransform().set( front );
				mapping.mapInterpolated( frontSource, target );

				face.pan( Math.PI / 2.0 );
				face.preConcatenateOrientation( front );
				mapping.getTransform().set( face );
				mapping.mapInterpolated( rightSource, target );

				face.resetOrientation();
				face.pan( Math.PI );
				face.preConcatenateOrientation( front );
				mapping.getTransform().set( face );
				mapping.mapInterpolated( backSource, target );

				face.resetOrientation();
				face.pan( 3.0 * Math.PI / 2.0 );
				face.preConcatenateOrientation( front );
				mapping.getTransform().set( face );
				mapping.mapInterpolated( leftSource, target );

				face.resetOrientation();
				face.tilt( Math.PI / 2.0 );
				face.preConcatenateOrientation( front );
				mapping.getTransform().set( face );
				mapping.mapInterpolated( topSource, target );

				face.resetOrientation();
				face.tilt( -Math.PI / 2.0 );
				face.preConcatenateOrientation( front );
				mapping.getTransform().set( face );
				mapping.mapInterpolated( bottomSource, target );
			}
			else
			{
				mapping.getTransform().set( front );
				mapping.map( frontSource, target );

				face.pan( Math.PI / 2.0 );
				face.preConcatenateOrientation( front );
				mapping.getTransform().set( face );
				mapping.map( rightSource, target );

				face.resetOrientation();
				face.pan( Math.PI );
				face.preConcatenateOrientation( front );
				mapping.getTransform().set( face );
				mapping.map( backSource, target );

				face.resetOrientation();
				face.pan( 3.0 * Math.PI / 2.0 );
				face.preConcatenateOrientation( front );
				mapping.getTransform().set( face );
				mapping.map( leftSource, target );

				face.resetOrientation();
				face.tilt( Math.PI / 2.0 );
				face.preConcatenateOrientation( front );
				mapping.getTransform().set( face );
				mapping.map( topSource, target );

				face.resetOrientation();
				face.tilt( -Math.PI / 2.0 );
				face.preConcatenateOrientation( front );
				mapping.getTransform().set( face );
				mapping.map( bottomSource, target );
			}
		}
	}

	final private class MappingThread extends AbstractMappingThread
	{
		final protected CubeFaceMapper mapper;
		final protected PanoramaCamera< ? > camera;

		public MappingThread(
				final ImagePlus impSource,
				final ImagePlus impTarget,
				final CubeFaceMapper mapper,
				final ImageProcessor target,
				final PanoramaCamera< ? > camera )
		{
			super( impSource, impTarget, target );
			this.mapper = mapper;
			this.camera = camera;
		}

		@Override
		final protected void map()
		{
			lambda += dt * dLambda;
			phi += dt * dPhi;

			this.camera.setOrientation( lambda, phi, rho );

			mapper.map( temp );
		}

		@Override
		final public void toggleInterpolation()
		{
			mapper.toggleInterpolation();
		}
	}

	private ImageProcessor frontSource;
	private ImageProcessor backSource;
	private ImageProcessor leftSource;
	private ImageProcessor rightSource;
	private ImageProcessor topSource;
	private ImageProcessor bottomSource;

	static private boolean showCubefaces = false;

	@Override
	protected boolean setup( final ImagePlus imp )
	{
		final GenericDialog gd = new GenericDialog( "Panorama Viewer" );

		gd.addMessage( "Panorama" );
		gd.addNumericField( "min lambda : ", minLambda / Math.PI * 180, 2 );
		gd.addNumericField( "min phi : ", minPhi / Math.PI * 180, 2 );
		gd.addNumericField( "hfov : ", hfov / Math.PI * 180, 2 );
		gd.addNumericField( "vfov : ", vfov / Math.PI * 180, 2 );

		gd.addMessage( "Viewer Window" );
		gd.addNumericField( "width : ", width, 0 );
		gd.addNumericField( "height : ", height, 0 );

		gd.addMessage( "Miscellaneous" );
		gd.addCheckbox( "show cube-faces", showCubefaces );

//		gd.addHelp( "http://fiji.sc/wiki/index.php/Enhance_Local_Contrast_(CLAHE)" );

		gd.showDialog();

		if ( gd.wasCanceled() ) return false;

		minLambda = Util.mod( gd.getNextNumber(), 360.0 ) / 180.0 * Math.PI;
		minPhi = Util.mod( gd.getNextNumber(), 180.0 ) / 180.0 * Math.PI;
		hfov = Math.min( Math.PI * 2.0 - minLambda, Util.mod( gd.getNextNumber(), 360.0 ) / 180.0 * Math.PI );
		vfov = Math.min( Math.PI - minPhi, Util.mod( gd.getNextNumber(), 180.0 ) / 180.0 * Math.PI );

		if ( hfov == 0 ) hfov = 2.0 * Math.PI;
		if ( vfov == 0 ) vfov = Math.PI;

		System.out.println( minLambda + " " + minPhi + " " + hfov + " " + vfov );

		width = ( int )gd.getNextNumber();
		height = ( int )gd.getNextNumber();

		showCubefaces = gd.getNextBoolean();

		return true;
	}

	private MappingThread painter;

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

	@Override
	protected MappingThread createPainter( final ImagePlus impViewer )
	{
		/* TODO calculate proper size */

		//final int cubeSize = 500;
		final int cubeSize = ( int )Math.round( Math.max( p.getPhiPiScale(), p.getLambdaPiScale() ) * 2.0 / Math.PI );

		frontSource = ip.createProcessor( cubeSize + 1, cubeSize + 1 );
		backSource = ip.createProcessor( cubeSize + 1, cubeSize + 1 );
		leftSource = ip.createProcessor( cubeSize + 1, cubeSize + 1 );
		rightSource = ip.createProcessor( cubeSize + 1, cubeSize + 1 );
		topSource = ip.createProcessor( cubeSize + 1, cubeSize + 1 );
		bottomSource = ip.createProcessor( cubeSize + 1, cubeSize + 1 );

		renderCubeFaces( hfov, vfov );

		/* instantiate and run mapper and painter */
		final CubeFaceMapper mapper = new CubeFaceMapper(
				p );

		return new MappingThread(
				imp,
				impViewer,
				mapper,
				ip,
				p );
    }

	final private void renderCubeFaces( final double hfov, final double vfov )
	{
		/* fragile, but that's not public API and we know what we're doing... */
		final double cubeSize = frontSource.getWidth() - 1;

		/* prepare extended image */
		ipSource = ip.createProcessor(
				hfov == 2.0 * Math.PI ? imp.getWidth() + 1 : imp.getWidth(),
				vfov == Math.PI ? imp.getHeight() + 1 : imp.getHeight() );
		prepareExtendedImage( imp.getProcessor(), ipSource );

		/* render cube faces */
		final EquirectangularProjection q = p.clone();
		q.resetOrientation();
		q.setTargetWidth( cubeSize );
		q.setTargetHeight( cubeSize );
		q.setF( 0.5f );

		final InverseTransformMapping< EquirectangularProjection > qMapping = new InverseTransformMapping< EquirectangularProjection >( q );

		IJ.showStatus( "Rendering cube faces..." );
		IJ.showProgress( 0, 6 );
		qMapping.mapInterpolated( ipSource, frontSource );
		IJ.showProgress( 1, 6 );
		q.pan( Math.PI );
		qMapping.mapInterpolated( ipSource, backSource );
		IJ.showProgress( 2, 6 );
		q.resetOrientation();
		q.pan( Math.PI / 2 );
		qMapping.mapInterpolated( ipSource, leftSource );
		IJ.showProgress( 3, 6 );
		q.resetOrientation();
		q.pan( -Math.PI / 2 );
		qMapping.mapInterpolated( ipSource, rightSource );
		IJ.showProgress( 4, 6 );
		q.resetOrientation();
		q.tilt( -Math.PI / 2 );
		qMapping.mapInterpolated( ipSource, topSource );
		IJ.showProgress( 5, 6 );
		q.resetOrientation();
		q.tilt( Math.PI / 2 );
		qMapping.mapInterpolated( ipSource, bottomSource );
		IJ.showProgress( 6, 6 );

		if ( showCubefaces )
		{
			new ImagePlus( "front", frontSource ).show();
			new ImagePlus( "back", backSource ).show();
			new ImagePlus( "left", leftSource ).show();
			new ImagePlus( "right", rightSource ).show();
			new ImagePlus( "top", topSource ).show();
			new ImagePlus( "bottom", bottomSource ).show();
		}
	}
}
