/*******************************************************************************
 * Copyright (C) 2015, Andre Bossert <anb0s@anbos.de>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.egit.ui.internal.externaltools;

/**
 * @author anb0s
 *
 */
public interface ITool {
	/**
	 * @return return the tool name
	 */
	public String getName();

	/**
	 * @return return the tool path
	 */
	public String getPath();

	/**
	 * @return return the tool options
	 */
	public String getOptions();

	/**
	 * @return return the whole command string
	 */
	public String getCommand();

	/**
	 * @return return the attribute set
	 */
	public AttributeSet getAttributeSet();

}
