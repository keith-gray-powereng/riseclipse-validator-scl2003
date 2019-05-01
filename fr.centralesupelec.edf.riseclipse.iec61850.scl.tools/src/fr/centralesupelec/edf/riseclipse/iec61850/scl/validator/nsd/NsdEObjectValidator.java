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
package fr.centralesupelec.edf.riseclipse.iec61850.scl.validator.nsd;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.DiagnosticChain;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EValidator;
import fr.centralesupelec.edf.riseclipse.iec61850.nsd.LNClass;
import fr.centralesupelec.edf.riseclipse.iec61850.nsd.util.NsdResourceSetImpl;
import fr.centralesupelec.edf.riseclipse.iec61850.scl.AnyLN;
import fr.centralesupelec.edf.riseclipse.iec61850.scl.LNodeType;
import fr.centralesupelec.edf.riseclipse.iec61850.scl.util.SclSwitch;
import fr.centralesupelec.edf.riseclipse.iec61850.scl.validator.RiseClipseValidatorSCL;
import fr.centralesupelec.edf.riseclipse.util.AbstractRiseClipseConsole;

public class NsdEObjectValidator implements EValidator {

    private NsdResourceSetImpl nsdResourceSet;
    private HashMap< String, AnyLNValidator > anyLNValidatorMap;
    private HashMap<String,LNodeTypeValidator> lNodeTypeValidatorMap;

    public NsdEObjectValidator( NsdResourceSetImpl nsdResourceSet ) {
        this.nsdResourceSet = nsdResourceSet;
    }

    public void initializeValidationData() {
        this.anyLNValidatorMap = this.nsdResourceSet.getLNClassStream()
                .map( lnClass -> generateAnyLNValidators( lnClass ) )
                .reduce( ( a, b ) -> {
                    a.putAll( b );
                    return a;
                } ).get();

        this.lNodeTypeValidatorMap = this.nsdResourceSet.getLNClassStream()
                .map( lnClass -> generateLNodeTypeValidators( lnClass ) )
                .reduce( ( a, b ) -> {
                    a.putAll( b );
                    return a;
                } ).get();
    }

    private HashMap< String, AnyLNValidator > generateAnyLNValidators( LNClass lnClass ) {
        HashMap< String, AnyLNValidator > lnMap = new HashMap<>();
        lnMap.put( lnClass.getName(), new AnyLNValidator( lnClass ));
        return lnMap;
    }

    private HashMap< String, LNodeTypeValidator > generateLNodeTypeValidators( LNClass lnClass ) {
        HashMap< String, LNodeTypeValidator > lNodeTypeMap = new HashMap<>();
        lNodeTypeMap.put( lnClass.getName(), new LNodeTypeValidator( lnClass ));
        return lNodeTypeMap;
    }

    @Override
    public boolean validate( EObject eObject, DiagnosticChain diagnostics, Map< Object, Object > context ) {
        return validate( eObject.eClass(), eObject, diagnostics, context );
    }

    @Override
    public boolean validate( EClass eClass, EObject eObject, DiagnosticChain diagnostics, Map< Object, Object > context ) {

        if( this.anyLNValidatorMap == null ) {
            this.initializeValidationData();
        }
        
        SclSwitch< Boolean > sw = new SclSwitch< Boolean >() {

            @Override
            public Boolean caseAnyLN( AnyLN anyLN ) {
                return validateAnyLN( anyLN, diagnostics );
            }

            @Override
            public Boolean caseLNodeType( LNodeType lNodeType ) {
                return validateLNodeType( lNodeType, diagnostics );
            }

            @Override
            public Boolean defaultCase( EObject object ) {
                AbstractRiseClipseConsole.getConsole().info( "NOT IMPLEMENTED: NsdEObjectValidator.validate( " + object.eClass().getName() + " )" );
                return true;
            }
            
        };

        return sw.doSwitch( eObject );
    }

    @Override
    public boolean validate( EDataType eDataType, Object value, DiagnosticChain diagnostics, Map< Object, Object > context ) {
        AbstractRiseClipseConsole.getConsole().info( "NOT IMPLEMENTED: NsdEObjectValidator.validate( " + eDataType.getName() + " )" );

        // TODO: use nsdResource to validate value

        return true;
    }

    private boolean validateAnyLN( AnyLN ln, DiagnosticChain diagnostics ) {
        AbstractRiseClipseConsole.getConsole().verbose( "" );
        AbstractRiseClipseConsole.getConsole().verbose( "NsdEObjectValidator.validateLN( " + ln.getLnClass() + " )" );

        // Check that LN has valid LNClass
        if( ! this.anyLNValidatorMap.containsKey( ln.getLnClass() )) {
            diagnostics.add( new BasicDiagnostic(
                    Diagnostic.ERROR,
                    RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                    0,
                    "LNClass " + ln.getLnClass() + " not found in NSD files for LN at line " + ln.getLineNumber(),
                    new Object[] { ln } ));
            return false;
        }
        AbstractRiseClipseConsole.getConsole().verbose( "found LNClass " + ln.getLnClass() + " in NSD files for LN at line " + ln.getLineNumber() );

        // AnyLNValidator validates LN content
        return anyLNValidatorMap.get( ln.getLnClass() ).validateLN( ln, diagnostics );
    }

    protected Boolean validateLNodeType( LNodeType lNodeType, DiagnosticChain diagnostics ) {
        AbstractRiseClipseConsole.getConsole().verbose( "" );
        AbstractRiseClipseConsole.getConsole().verbose( "NsdEObjectValidator.validateLNodeType( " + lNodeType.getLnClass() + " )" );

        // Check that LN has valid LNClass
        if( ! this.anyLNValidatorMap.containsKey( lNodeType.getLnClass() )) {
            diagnostics.add( new BasicDiagnostic(
                    Diagnostic.ERROR,
                    RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                    0,
                    "LNClass " + lNodeType.getLnClass() + " not found in NSD files for LN at line " + lNodeType.getLineNumber(),
                    new Object[] { lNodeType } ));
            return false;
        }
        AbstractRiseClipseConsole.getConsole().verbose( "found LNClass " + lNodeType.getLnClass() + " in NSD files for LN at line " + lNodeType.getLineNumber() );

        // AnyLNValidator validates LN content
        return lNodeTypeValidatorMap.get( lNodeType.getLnClass() ).validateLNodeType( lNodeType, diagnostics );
    }

}
