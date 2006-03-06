/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.viewers.provisional;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Composite;

/**
 * Defines the possible set of columns presented in a view for a model.
 * A column presentation is customizable per presentation context (view) and
 * view input, and is created by an <code>IColumnPresentationFactoryAdapter</code>.
 * 
 * @since 3.2
 */
public interface IColumnPresentation {
	
	/**
	 * Initializes this column presentation to be used in the
	 * given context.
	 * 
	 * @param context
	 */
	public void init(IPresentationContext context);
	
	/**
	 * Disposes tihs column presentation
	 */
	public void dispose();
	
	/**
	 * Returns an array of all columns supported by this adapter as
	 * column identifiers.
	 * 
	 * @return column identifiers
	 */
	public String[] getAvailableColumns();
	
	/**
	 * Returns an ordered array of columns that should be displayed initially for
	 * this presentation as column ids.
	 * 
	 * @return initial columns to display
	 */
	public String[] getInitialColumns();
	
	/**
	 * Returns the column header for the column with the given identifier.
	 * 
	 * @param id a column identifier included in <code>getColumns()</code>
	 * @return column header
	 */
	public String getHeader(String id);
	
	/**
	 * Returns the image for the column with the given identifier, or <code>null</code>
	 * 
	 * @param id column id
	 * @return image descriptor or <code>null</code>
	 */
	public ImageDescriptor getImageDescriptor(String id);
	
	/**
	 * Returns a cell editor to use for the specified column and object or <code>null</code>
	 * if none.
	 * 
	 * @param id column id
	 * @param element object to be edited
	 * @return cell editor or <code>null</code>
	 */
	public Composite getCellEditor(String id, Object element);
	
	/**
	 * Returns an identifier for this columns presentation adapter.
	 * The identifier should be unique per kind of column presentation 
	 * adapter (for example, the column adapter for Java stack frames
	 * in the variables view). Allows visible column information to be
	 * persisted by the platform.
	 * 
	 * @return identifier
	 */
	public String getId();

}