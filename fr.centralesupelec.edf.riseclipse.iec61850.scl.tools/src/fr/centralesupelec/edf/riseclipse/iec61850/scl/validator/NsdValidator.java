/**
 *  Copyright (c) 2019 CentraleSupélec & EDF.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  This file is part of the RiseClipse tool
 *  
 *  Contributors:
 *      Computer Science Department, CentraleSupélec
 *      EDF R&D
 *  Contacts:
 *      dominique.marcadet@centralesupelec.fr
 *      aurelie.dehouck-neveu@edf.fr
 *  Web site:
 *      http://wdi.supelec.fr/software/RiseClipse/
 */
package fr.centralesupelec.edf.riseclipse.iec61850.scl.validator;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.ocl.pivot.validation.ComposedEValidator;

import fr.centralesupelec.edf.riseclipse.iec61850.nsd.utilities.NsdModelLoader;
import fr.centralesupelec.edf.riseclipse.util.IRiseClipseConsole;

public class NsdValidator {

    private NsdModelLoader nsdLoader;

    public NsdValidator( @NonNull EPackage modelPackage, IRiseClipseConsole console ) {
        nsdLoader = new NsdModelLoader( console );
    }

    public void addNsdDocument( String nsdFile, IRiseClipseConsole console ) {
        console.info( "Loading nsd: " + nsdFile );
        nsdLoader.load( nsdFile );
    }
    
    public void prepare( @NonNull ComposedEValidator validator, IRiseClipseConsole console ) {
        nsdLoader.getResourceSet().finalizeLoad( console );
        NsdEObjectValidator nsdEObjectValidator = new NsdEObjectValidator( nsdLoader.getResourceSet() );
        nsdEObjectValidator.initializeValidationData();
        validator.addChild( nsdEObjectValidator );
    }

}
