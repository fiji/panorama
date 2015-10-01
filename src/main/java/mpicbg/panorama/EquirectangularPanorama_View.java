package mpicbg.panorama;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;
import mpicbg.ij.InverseTransformMapping;
import mpicbg.ij.Mapping;

public class EquirectangularPanorama_View extends AbstractPanorama_View
{
	final private class MappingThread extends AbstractMappingThread
	{
		final protected ImageProcessor source;
		final protected Mapping< EquirectangularProjection > eqiMapping;
		final protected EquirectangularProjection projection;
		private boolean interpolate = true;

		public MappingThread(
				final ImagePlus impSource,
				final ImagePlus impTarget,
				final ImageProcessor source,
				final ImageProcessor target,
				final Mapping< EquirectangularProjection > mapping,
				final EquirectangularProjection p )
		{
			super( impSource, impTarget, target );
			this.source = source;
			this.eqiMapping = mapping;
			this.projection = p;
			this.setName( "MappingThread" );
		}

		@Override
		final public void map()
		{
			lambda += dt * dLambda;
			phi += dt * dPhi;

			projection.setOrientation( lambda, phi, rho );

			eqiMapping.getTransform().set( projection );
			temp.reset();
			if ( interpolate )
				eqiMapping.mapInterpolated( source, temp );
			else
				eqiMapping.map( source, temp );
		}

		@Override
		final public void toggleInterpolation()
		{
			interpolate = !interpolate;
		}
	}

	final private Mapping< EquirectangularProjection > mapping = new InverseTransformMapping< EquirectangularProjection >( p.clone() );

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

		gd.showDialog();

		if ( gd.wasCanceled() ) return false;

		minLambda = Util.mod( gd.getNextNumber(), 360.0 ) / 180.0 * Math.PI;
		minPhi = Util.mod( gd.getNextNumber(), 180.0 ) / 180 * Math.PI;
		hfov = Math.min( Math.PI * 2 - minLambda, Util.mod( gd.getNextNumber(), 360.0 ) / 180.0 * Math.PI );
		vfov = Math.min( Math.PI - minPhi, Util.mod( gd.getNextNumber(), 180.0 ) / 180.0 * Math.PI );

		if ( hfov == 0 ) hfov = 2.0 * Math.PI;
		if ( vfov == 0 ) vfov = Math.PI;

		System.out.println( minLambda + " " + minPhi + " " + hfov + " " + vfov );

		width = ( int )gd.getNextNumber();
		height = ( int )gd.getNextNumber();

		return true;
	}

	private MappingThread painter;

	@Override
	protected AbstractMappingThread createPainter( final ImagePlus impViewer )
	{
		/* prepare extended image */
		ipSource = ip.createProcessor(
				hfov == 2.0 * Math.PI ? imp.getWidth() + 1 : imp.getWidth(),
				vfov == Math.PI ? imp.getHeight() + 1 : imp.getHeight() );
		prepareExtendedImage( imp.getProcessor(), ipSource );
		return new MappingThread( imp, impViewer, ipSource, ip, mapping, p );
	}
}
