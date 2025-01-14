/*
*************************************************************************
**  Copyright (c) 2016-2022 CentraleSupélec & EDF.
**  All rights reserved. This program and the accompanying materials
**  are made available under the terms of the Eclipse Public License v2.0
**  which accompanies this distribution, and is available at
**  https://www.eclipse.org/legal/epl-v20.html
** 
**  This file is part of the RiseClipse tool
**  
**  Contributors:
**      Computer Science Department, CentraleSupélec
**      EDF R&D
**  Contacts:
**      dominique.marcadet@centralesupelec.fr
**      aurelie.dehouck-neveu@edf.fr
**  Web site:
**      https://riseclipse.github.io/
*************************************************************************
*/
package fr.centralesupelec.edf.riseclipse.iec61850.scl.validator.nsd;

import java.util.HashSet;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.DiagnosticChain;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EValidator;

import fr.centralesupelec.edf.riseclipse.iec61850.nsd.util.NsdResourceSetImpl;
import fr.centralesupelec.edf.riseclipse.iec61850.nsd.util.NsIdentification;
import fr.centralesupelec.edf.riseclipse.iec61850.nsd.util.NsIdentificationName;
import fr.centralesupelec.edf.riseclipse.iec61850.scl.AnyLN;
import fr.centralesupelec.edf.riseclipse.iec61850.scl.LNodeType;
import fr.centralesupelec.edf.riseclipse.iec61850.scl.util.SclSwitch;
import fr.centralesupelec.edf.riseclipse.iec61850.scl.validator.RiseClipseValidatorSCL;
import fr.centralesupelec.edf.riseclipse.util.AbstractRiseClipseConsole;
import fr.centralesupelec.edf.riseclipse.util.IRiseClipseConsole;
import fr.centralesupelec.edf.riseclipse.util.RiseClipseMessage;

public class NsdEObjectValidator implements EValidator {

    private NsdResourceSetImpl nsdResourceSet;
    private HashSet< NsIdentificationName > validatedLNodeTypes = new HashSet<>();  

    public NsdEObjectValidator( NsdResourceSetImpl nsdResourceSet, IRiseClipseConsole console ) {
        // We keep it to improve some error messages
        this.nsdResourceSet = nsdResourceSet;
        
        // To avoid building several times the validators, we process the ordered list of NsIdentification (root first)
        
        // CDC "ENS" in 61850-7-3 is enumParameterized
        // AbstractLNClass "DomainLN" in 61850-7-4 uses it with enumeration "BehaviourModeKind" which is also defined in 61850-7-4
        // The tool makes a copy of the CDC and sets the right type to the appropriate DataAttribute, but the CDC is still in 61850-7-3
        // Validators for 61850-7-3 are built before those of 61850-7-4, and when the one for the instantiated CDC is built,
        // validator for "BehaviourModeKind" is not yet built and therefore not found
        // This is why there are several loops
        for( NsIdentification nsIdentification : nsdResourceSet.getNsIdentificationOrderedList( console )) {
            console.debug( NsdValidator.SETUP_NSD_CATEGORY, 0, "Building basic and enumeration validators in namespace \"", nsIdentification, "\"" );
            // Order is important !
            TypeValidator.buildBasicTypeValidators(
                    nsIdentification,
                    nsdResourceSet.getBasicTypeStream( nsIdentification, false ),
                    console );
            TypeValidator.builEnumerationdValidators(
                    nsIdentification,
                    nsdResourceSet.getEnumerationStream( nsIdentification, false ),
                    console );
        }
        for( NsIdentification nsIdentification : nsdResourceSet.getNsIdentificationOrderedList( console )) {
            console.debug( NsdValidator.SETUP_NSD_CATEGORY, 0, "Building constructed attributes validators in namespace \"", nsIdentification, "\"" );
            TypeValidator.buildConstructedAttributeValidators(
                    nsIdentification,
                    nsdResourceSet.getConstructedAttributeStream( nsIdentification, false ),
                    console );
        }
        for( NsIdentification nsIdentification : nsdResourceSet.getNsIdentificationOrderedList( console )) {
            console.debug( NsdValidator.SETUP_NSD_CATEGORY, 0, "Building CDC validators in namespace \"", nsIdentification, "\"" );
            CDCValidator.buildValidators(
                    nsIdentification,
                    nsdResourceSet.getCDCStream( nsIdentification, false ),
                    console );
        }
        for( NsIdentification nsIdentification : nsdResourceSet.getNsIdentificationOrderedList( console )) {
            console.debug( NsdValidator.SETUP_NSD_CATEGORY, 0, "Building LNClass validators in namespace \"", nsIdentification, "\"" );
            LNClassValidator.buildValidators(
                    nsIdentification,
                    nsdResourceSet.getLNClassStream( nsIdentification, false ),
                    console );
        }
    }

    /*
     * Called before another file is validated
     */
    public void reset() {
        TypeValidator.resetValidators();
        CDCValidator.resetValidators();
        LNClassValidator.resetValidators();
    }

    @Override
    public boolean validate( EObject eObject, DiagnosticChain diagnostics, Map< Object, Object > context ) {
        return validate( eObject.eClass(), eObject, diagnostics, context );
    }

    @Override
    public boolean validate( EClass eClass, EObject eObject, DiagnosticChain diagnostics, Map< Object, Object > context ) {

        SclSwitch< Boolean > sw = new SclSwitch< Boolean >() {

            @Override
            public Boolean caseAnyLN( AnyLN anyLN ) {
                AbstractRiseClipseConsole.getConsole().debug( NsdValidator.VALIDATION_NSD_CATEGORY, anyLN.getFilename(), anyLN.getLineNumber(),
                                                              "NsdEObjectValidator.validate( type=\"", anyLN.getLnType(), "\" class=\"", anyLN.getLnClass(), "\" )" );
                if( anyLN.getRefersToLNodeType() == null ) {
                    RiseClipseMessage warning = RiseClipseMessage.warning( NsdValidator.VALIDATION_NSD_CATEGORY, anyLN.getFilename(), anyLN.getLineNumber(),
                            "AnyLN type=\"", anyLN.getLnType(), "\" class=\"", anyLN.getLnClass(), "\" has no associated LNodeType" );
                    diagnostics.add( new BasicDiagnostic(
                            Diagnostic.WARNING,
                            RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                            0,
                            warning.getMessage(),
                            new Object[] { anyLN, warning } ));
                    return true;
                }
                if(( anyLN.getNamespace() == null ) || ( anyLN.getNamespace().isEmpty() )) {
                    RiseClipseMessage warning = RiseClipseMessage.warning( NsdValidator.VALIDATION_NSD_CATEGORY, anyLN.getFilename(), anyLN.getLineNumber(),
                            "AnyLN type=\"", anyLN.getLnType(), "\" class=\"", anyLN.getLnClass(), "\" has no namespace" );
                    diagnostics.add( new BasicDiagnostic(
                            Diagnostic.WARNING,
                            RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                            0,
                            warning.getMessage(),
                            new Object[] { anyLN, warning } ));
                    return true;
                }
                NsIdentificationName nsId = NsIdentificationName.of( anyLN.getNamespace(), anyLN.getRefersToLNodeType().getId() );
                if( validatedLNodeTypes.contains( nsId )) {
                    AbstractRiseClipseConsole.getConsole().debug( NsdValidator.VALIDATION_NSD_CATEGORY, anyLN.getFilename(), anyLN.getLineNumber(),
                            "LNodeType id=\"", anyLN.getRefersToLNodeType().getId(), "\" has already been validated in namespace \"", anyLN.getNamespace(), "\"" );
                    return true;
                }
                validatedLNodeTypes.add( nsId );
                return validateLNodeType( anyLN.getRefersToLNodeType(), anyLN.getNamespace(), diagnostics );
            }

            @Override
            public Boolean defaultCase( EObject object ) {
//                AbstractRiseClipseConsole.getConsole().info( "NOT IMPLEMENTED: NsdEObjectValidator.validate( " + object.eClass().getName() + " )" );
                return true;
            }
            
        };

        return sw.doSwitch( eObject );
    }

    @Override
    public boolean validate( EDataType eDataType, Object value, DiagnosticChain diagnostics, Map< Object, Object > context ) {
        return true;
    }

   private boolean validateLNodeType( LNodeType lNodeType, String namespace, DiagnosticChain diagnostics ) {
        IRiseClipseConsole console = AbstractRiseClipseConsole.getConsole();
        console.debug( NsdValidator.VALIDATION_NSD_CATEGORY, lNodeType.getFilename(), lNodeType.getLineNumber(),
                       "NsdEObjectValidator.validateLNodeType( ", lNodeType.getId(), " in namespace ", namespace );

        NsIdentification id = NsIdentification.of( namespace );
        if( nsdResourceSet.getNS( id ) == null ) {
            RiseClipseMessage warning = RiseClipseMessage.warning( NsdValidator.VALIDATION_NSD_CATEGORY, lNodeType.getFilename(), lNodeType.getLineNumber(), 
                      "Cannot validate LNodeType ", lNodeType.getId(), " in namespace \"", namespace, "\" because this namespace is unknown" );
            diagnostics.add( new BasicDiagnostic(
                    Diagnostic.WARNING,
                    RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                    0,
                    warning.getMessage(),
                    new Object[] { lNodeType, warning } ));
            return false;
        }
        // Check that LNodeType has a known LNClass in the given namespace
        Pair< LNClassValidator, NsIdentification > lnClassValidator = LNClassValidator.get( id, lNodeType.getLnClass() );
        if( lnClassValidator.getLeft() != null ) {
            console.notice( NsdValidator.VALIDATION_NSD_CATEGORY, lNodeType.getFilename(), lNodeType.getLineNumber(),
                           "LNClass ", lNodeType.getLnClass(), " found for LNodeType in namespace \"" + lnClassValidator.getRight() + "\"" );

            return lnClassValidator.getLeft().validateLNodeType( lNodeType, diagnostics );
        }
        
        RiseClipseMessage error = RiseClipseMessage.error( NsdValidator.VALIDATION_NSD_CATEGORY, lNodeType.getFilename(), lNodeType.getLineNumber(), 
                  "LNClass ", lNodeType.getLnClass(), " not found for LNodeType in namespace \"", namespace, "\"" );
        diagnostics.add( new BasicDiagnostic(
                Diagnostic.ERROR,
                RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                0,
                error.getMessage(),
                new Object[] { lNodeType, error } ));
        return false;
    }
}
