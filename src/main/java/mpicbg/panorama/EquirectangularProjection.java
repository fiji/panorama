package mpicbg.panorama;

import mpicbg.models.NoninvertibleModelException;

/**
 * A rectlinear projection from equirectangular coordinates (longitude,
 * latitude). The rectlinear frame is defined by the
 * {@link #getF() focal length}, its dimensions in #get<i>x</i> and <i>y</i>
 *
 * @author Stephan Saalfeld
 */
public class EquirectangularProjection extends PanoramaCamera< EquirectangularProjection >
{

	/* the equirectangular map */
	private double lambdaPiScale = 0;
	final public double getLambdaPiScale(){ return lambdaPiScale; }
	final public void setLambdaPiScale( final double lambdaPiScale ){ this.lambdaPiScale = lambdaPiScale; }

	private double phiPiScale = 0;
	final public double getPhiPiScale(){ return phiPiScale; }
	final public void setPhiPiScale( final double phiPiScale ){ this.phiPiScale = phiPiScale; }

	private double minLambda = 0;
	final public double getMinLambda(){ return minLambda; }
	final public void setMinLambda( final double minLambda ){ this.minLambda = minLambda; }

	private double minPhi = 0;
	final public double getMinPhi(){ return minPhi; }
	final public void setMinPhi( final double minPhi ){ this.minPhi = minPhi; }

	@Override
	final public double[] apply( final double[] point )
	{
		assert point.length == 2 : "2d projections can be applied to 2d points only.";

		final double[] t = point.clone();
		applyInPlace( t );
		return t;
	}

	@Override
	final public void applyInPlace( final double[] point )
	{
		assert point.length == 2 : "2d projections can be applied to 2d points only.";

		System.err.println( "Not yet implemented.  Please feel free to do it yourself." );
		// TODO implement it
	}

	@Override
	final public double[] applyInverse( final double[] point ) throws NoninvertibleModelException
	{
		assert point.length == 2 : "2d projections can be applied to 2d points only.";

		final double[] t = point.clone();
		applyInPlace( t );
		return null;
	}

	@Override
	final public void applyInverseInPlace( final double[] point ) throws NoninvertibleModelException
	{
		assert point.length == 2 : "2d projections can be applied to 2d points only.";

		/* scale with respect to targetHeight and f */
		final double x = ( point[ 0 ] - 0.5 * targetWidth ) / targetMaxSize;
		final double y = ( point[ 1 ] - 0.5 * targetHeight ) / targetMaxSize;

		/* calculate sphere cut */
		final double t = 1.0 / Math.sqrt( x * x + y * y + f * f );

		final double tx = t * x;
		final double ty = t * y;
		final double tz = t * f;

		/* rotate */
		final double rx = i.m00 * tx + i.m01 * ty  + i.m02 * tz;
		final double ry = i.m10 * tx + i.m11 * ty  + i.m12 * tz;
		final double rz = i.m20 * tx + i.m21 * ty  + i.m22 * tz;

		/* calculate phi and lambda */
		final double tLambda;
		if ( rz < 0 )
			tLambda = ( Math.asin( -rx / Math.sqrt( rx * rx + rz * rz ) ) + Math.PI - minLambda ) / Math.PI;
		else
			tLambda = ( Math.asin( rx / Math.sqrt( rx * rx + rz * rz ) ) - minLambda ) / Math.PI;

		point[ 0 ] = Util.mod( tLambda, 2 );
		point[ 1 ] = ( Math.asin( ry ) - minPhi ) / Math.PI + 0.5;

		point[ 0 ] = point[ 0 ] * lambdaPiScale;
		point[ 1 ] = point[ 1 ] * phiPiScale;
	}

	@Override
	final public EquirectangularProjection clone()
	{
		final EquirectangularProjection c = new EquirectangularProjection();

		c.set( this );

		return c;
	}

	@Override
    final public void set( final EquirectangularProjection e )
	{
		super.set( e );

		lambdaPiScale = e.lambdaPiScale;
		minLambda = e.minLambda;
		minPhi = e.minPhi;
		phiPiScale = e.phiPiScale;
	}

	@Override
	final public String toString()
	{
		return "";
	}

	/**
	 * TODO Not yet tested
	 */
	@Override
    final public EquirectangularProjection createInverse()
	{
		final EquirectangularProjection ict = new EquirectangularProjection();
		return ict;

	}
}
