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

package org.eclipse.debug.internal.ui.viewers.update;

import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.internal.ui.viewers.provisional.IModelSelectionPolicy;
import org.eclipse.debug.internal.ui.viewers.provisional.IModelSelectionPolicyFactoryAdapter;
import org.eclipse.debug.internal.ui.viewers.provisional.IPresentationContext;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.ui.IWorkbenchPart;

/**
 * Default factory for selection policies.
 * 
 * @since 3.2
 */
public class DefaultModelSelectionPolicyFactory implements IModelSelectionPolicyFactoryAdapter {

	public IModelSelectionPolicy createModelSelectionPolicyAdapter(Object element, IPresentationContext context) {
		IWorkbenchPart part = context.getPart();
		if (part != null && IDebugUIConstants.ID_DEBUG_VIEW.equals(part.getSite().getId())) {
			if (element instanceof IDebugElement) {
				return new DefaultSelectionPolicy((IDebugElement)element);
			}
		}
		return null;
	}

}