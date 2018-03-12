package org.eclipse.egit.ui.internal.trace;

/**
 * Just a place holder for OSGI Debug Trace support until
 * we drop 3.4 support in EGit/JGit;
 *
 * TODO replace this by OSGI's DebugTrace once we drop 3.4 support
 */
public interface DebugTrace {

	/**
	 * @param location
	 * @param message
	 */
	public void trace(String location, String message);

	/**
	 * @param location
	 * @param message
	 * @param error
	 */
	public void trace(String location, String message, Throwable error);

	/**
	 * @param location
	 */
	public void traceEntry(String location);

	/**
	 * @param location
	 * @param message
	 */
	public void traceEntry(String location, String message);

}
