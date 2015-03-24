package mpicbg.panorama;

import mpicbg.models.InvertibleCoordinateTransform;
import mpicbg.util.Matrix3x3;

/**
 * Essentially, a simplified homography that allows panning (&lambda;), tilting
 * (&phi;) and and zooming (f) only.
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public abstract class PanoramaCamera< T extends PanoramaCamera< T > > implements InvertibleCoordinateTransform
{
	/* orientation */
	final protected Matrix3x3 m = new Matrix3x3();

	/* inverse orientation */
	final protected Matrix3x3 i = new Matrix3x3();

	/* focal length */
	protected double f = 1;
	final public double getF(){ return f; }
	final public void setF( final double f ){ this.f = f; }

	/**
	 *
	 * @param lambda
	 * @param phi
	 * @param rho
	 */
	final public void setOrientation(
			final double lambda,
			final double phi,
			final double rho )
	{
		final double sinLambda = Math.sin( lambda );
		final double cosLambda = Math.cos( lambda );

		final double sinPhi = Math.sin( phi );
		final double cosPhi = Math.cos( phi );

		final double sinRho = Math.sin( rho );
		final double cosRho = Math.cos( rho );

		/* TODO calculate m */

		/* TODO reduce this */
		final Matrix3x3 panInverse = new Matrix3x3(
				cosLambda, 0, -sinLambda,
				0, 1, 0,
				sinLambda, 0, cosLambda );

		final Matrix3x3 tiltInverse = new Matrix3x3(
				1, 0, 0,
				0, cosPhi, sinPhi,
				0, -sinPhi, cosPhi );

		final Matrix3x3 rollInverse = new Matrix3x3(
				cosRho, sinRho, 0,
				-sinRho, cosRho, 0,
				0, 0, 1 );

		i.set( rollInverse );
		i.preConcatenate( tiltInverse );
		i.preConcatenate( panInverse );
	}

	final public void pan( final double lambda )
	{
		final double cosLambda = Math.cos( lambda );
		final double sinLambda = Math.sin( lambda );

		/* TODO calculate m */

		/* TODO reduce this */
		final Matrix3x3 panInverse = new Matrix3x3(
				cosLambda, 0, -sinLambda,
				0, 1, 0,
				sinLambda, 0, cosLambda );
		i.concatenate( panInverse );
	}

	final public void tilt( final double phi )
	{
		final double cosPhi = Math.cos( phi );
		final double sinPhi = Math.sin( phi );

		/* TODO calculate m */

		/* TODO reduce this */
		final Matrix3x3 tiltInverse = new Matrix3x3(
				1, 0, 0,
				0, cosPhi, sinPhi,
				0, -sinPhi, cosPhi );
		i.concatenate( tiltInverse );
	}

	final public void roll( final double rho )
	{
		final double cosRho = Math.cos( rho );
		final double sinRho = Math.sin( rho );

		/* TODO calculate m */

		/* TODO reduce this */
		final Matrix3x3 panInverse = new Matrix3x3(
				cosRho, sinRho, 0,
				-sinRho, cosRho, 0,
				0, 0, 1 );
		i.concatenate( panInverse );
	}

	/* the target (camera plane)*/
	protected double targetMaxSize = 0;

	protected double targetWidth = 0;
	protected double targetWidth2 = 0;
	final public double getTargetWidth(){ return targetWidth; }
	final public void setTargetWidth( final double targetWidth )
	{
		this.targetWidth = targetWidth;
		targetWidth2 = 0.5f * targetWidth;
		targetMaxSize = Math.max( targetWidth, targetHeight );
	}

	protected double targetHeight = 0;
	protected double targetHeight2 = 0;
	final public double getTargetHeight(){ return targetHeight; }
	final public void setTargetHeight( final double targetHeight )
	{
		this.targetHeight = targetHeight;
		targetHeight2 = 0.5 * targetHeight;
		targetMaxSize = Math.max( targetWidth, targetHeight );
	}


	final public void resetOrientation()
	{
		m.reset();
		i.reset();
	}


	final public void concatenateOrientation( final T p )
	{
		m.concatenate( p.m );
		i.preConcatenate( p.i );
	}


	final public void preConcatenateOrientation( final T p )
	{
		m.preConcatenate( p.m );
		i.concatenate( p.i );
	}

	final public void setCamera( final PanoramaCamera< ? > c )
	{
		f = c.f;
		i.set( c.i );
		m.set( c.m );
	}

	@Override
	abstract public T clone();

	public void set( final T t )
	{
		f = t.f;
		i.set( t.i );
		m.set( t.m );
		targetHeight = t.targetHeight;
		targetHeight2 = t.targetHeight2;
		targetMaxSize = t.targetMaxSize;
		targetWidth = t.targetWidth;
		targetWidth2 = t.targetWidth2;
	}
}
