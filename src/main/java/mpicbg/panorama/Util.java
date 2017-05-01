package mpicbg.panorama;

public class Util
{
	private Util(){}

	/**
	 * An equivalent to % for double
	 *
	 * @param a
	 * @param mod
	 * @return 0 &lt;= b &lt; mod
	 */
	final static public double mod( final double a, final double mod )
	{
		final double b = a - mod * ( long ) ( a / mod );
		if ( b >= 0 ) return b;
		else return b + mod;
	}
}
