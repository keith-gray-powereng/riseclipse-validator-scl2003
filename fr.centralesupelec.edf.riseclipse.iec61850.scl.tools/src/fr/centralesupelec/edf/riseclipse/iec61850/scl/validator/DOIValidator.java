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

import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.DiagnosticChain;

import fr.centralesupelec.edf.riseclipse.iec61850.nsd.CDC;
import fr.centralesupelec.edf.riseclipse.iec61850.nsd.DataAttribute;
import fr.centralesupelec.edf.riseclipse.iec61850.scl.DAI;
import fr.centralesupelec.edf.riseclipse.iec61850.scl.DOI;
import fr.centralesupelec.edf.riseclipse.iec61850.scl.Val;
import fr.centralesupelec.edf.riseclipse.util.AbstractRiseClipseConsole;

public class DOIValidator {

    private String cdc;
    private HashMap< String, DataAttribute > daMap;

    public DOIValidator( CDC cdc ) {
        this.cdc = cdc.getName();
        this.daMap = new HashMap<>(); // link between DAI (name) and its respective DataAttribute
        
        for( DataAttribute da : cdc.getDataAttribute() ) {
            this.daMap.put( da.getName(), da );
        }
    }

    public boolean validateDOI( DOI doi, DiagnosticChain diagnostics ) {
        boolean res = true;
        HashSet< String > checkedDA = new HashSet<>();

        for( DAI dai : doi.getDAI() ) {
            AbstractRiseClipseConsole.getConsole().verbose( "validateDAI( " + dai.getName() + " ) (line" + dai.getLineNumber() + ")" );

            // Test if DAI is a possible DAI in this DOI
            if( ! daMap.containsKey( dai.getName() ) ) {
                diagnostics.add( new BasicDiagnostic(
                        Diagnostic.ERROR,
                        RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                        0,
                        "DAI " + dai.getName() + " (line" + dai.getLineNumber() + ") not found in CDC",
                        new Object[] { doi, cdc } ));
                res = false;
                continue;
            }

            // Control of DAI presence in DOI
            updateCompulsory( dai, checkedDA, diagnostics );

            // Validation of DAI content
            if( ! validateDAI( dai, diagnostics ) ) {
                res = false;
            }

        }

        // Verify all necessary DAI were present
        if( ! daMap.values().stream()
                .map( x -> checkCompulsory( doi, x, checkedDA, diagnostics ) )
                .reduce( ( a, b ) -> a && b ).get() ) {
            diagnostics.add( new BasicDiagnostic(
                    Diagnostic.ERROR,
                    RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                    0,
                    "DOI (line " + doi.getLineNumber() + ") does not contain all mandatory DA from CDC ",
                    new Object[] { doi, cdc } ));
            res = false;
        }
        return res;
    }

    public boolean checkCompulsory( DOI doi, DataAttribute da, HashSet< String > checked, DiagnosticChain diagnostics ) {
        switch( da.getPresCond() ) {
        case "M":
            if( ! checked.contains( da.getName() )) {
                diagnostics.add( new BasicDiagnostic(
                        Diagnostic.ERROR,
                        RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                        0,
                        "DA " + da.getName() + " not found in DOI (line " + doi.getLineNumber() + ")",
                        new Object[] { da } ));
                return false;
            }
        }
        return true;
    }

    public boolean updateCompulsory( DAI dai, HashSet< String > checked, DiagnosticChain diagnostics ) {
        switch( daMap.get( dai.getName() ).getPresCond() ) {
        case "M":
        case "O":
            if( checked.contains( dai.getName() ) ) {
                diagnostics.add( new BasicDiagnostic(
                        Diagnostic.ERROR,
                        RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                        0,
                        "DAI " + dai.getName() + " (line " + dai.getLineNumber() + ") cannot appear more than once",
                        new Object[] { dai } ));
                return false;
            }
            else {
                checked.add( dai.getName() );
                break;
            }
        case "F":
            diagnostics.add( new BasicDiagnostic(
                    Diagnostic.ERROR,
                    RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                    0,
                    "DAI " + dai.getName() + " (line " + dai.getLineNumber() + ") is forbidden",
                    new Object[] { dai } ));
            return false;
        }
        return true;
    }

    public boolean validateDAI( DAI dai, DiagnosticChain diagnostics ) {

        AbstractRiseClipseConsole.getConsole().verbose( "found DA " + dai.getName() + " in CDC " + cdc );

        // DataAttributes that are BASIC have a BasicType which describes allowed Val of DA
        DataAttribute da = daMap.get( dai.getName() );
        if( da.getTypeKind().getName().equals( "BASIC" ) ) {
            for( Val val : dai.getVal() ) {
                if( ! validateVal( val.getValue(), da.getType() )) {
                    diagnostics.add( new BasicDiagnostic(
                            Diagnostic.ERROR,
                            RiseClipseValidatorSCL.DIAGNOSTIC_SOURCE,
                            0,
                            "Val " + val.getValue() + " (" + dai.getLineNumber() + ") of DA " + dai.getName() + " is not of type " + da.getType(),
                            new Object[] { dai, val } ));
                     return false;
                }
                AbstractRiseClipseConsole.getConsole().verbose( "Val " + val.getValue() + " (" + dai.getLineNumber() + ") of DA " + dai.getName() +
                        " is of type " + da.getType() );
            }
        }

        return true;
    }

    public boolean validateVal( String val, String type ) {
        int v;
        long l;
        float f;
        switch( type ) {
        case "BOOLEAN":
            return( val.equals( "0" ) || val.equals( "1" ) || val.equals( "false" ) || val.equals( "true" ) );
        case "INT8":
            try {
                v = Integer.parseInt( val );
            }
            catch( Exception e ) {
                return false;
            }
            return v >= -128 && v <= 127;
        case "INT16":
            try {
                v = Integer.parseInt( val );
            }
            catch( Exception e ) {
                return false;
            }
            return v >= -32768 && v <= 32767;
        case "INT32":
            try {
                v = Integer.parseInt( val );
            }
            catch( Exception e ) {
                return false;
            }
            return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE;
        case "INT64":
            try {
                l = Long.parseLong( val );
            }
            catch( Exception e ) {
                return false;
            }
            return l >= Long.MIN_VALUE && l <= Long.MAX_VALUE;
        case "INT8U":
            try {
                v = Integer.parseInt( val );
            }
            catch( Exception e ) {
                return false;
            }
            return v >= 0 && v <= 255;
        case "INT16U":
            try {
                v = Integer.parseInt( val );
            }
            catch( Exception e ) {
                return false;
            }
            return v >= 0 && v <= 65535;
        case "INT32U":
            try {
                l = Long.parseLong( val );
            }
            catch( Exception e ) {
                return false;
            }
            String max = "4294967295";
            return l >= 0 && l <= Long.parseLong( max );
        case "FLOAT32":
            try {
                f = Float.parseFloat( val );
            }
            catch( Exception e ) {
                return false;
            }
            return f >= -Float.MAX_VALUE && f <= Float.MAX_VALUE;
        case "Octet64":
            byte[] bytes = val.getBytes();
            return bytes.length <= 64;
        case "VisString64":
            return val.length() <= 64;
        case "VisString129":
            return val.length() <= 129;
        case "Unicode255":
        case "VisString255":
            return val.length() <= 255;
        default:
            return true;
        }
    }

    private void testValidateVal() {
        log( "\n--\tSTART TEST\t--\n" );
        assertTrue( validateVal( "0", "BOOLEAN" ) );
        assertTrue( validateVal( "1", "BOOLEAN" ) );
        assertTrue( validateVal( "true", "BOOLEAN" ) );
        assertTrue( validateVal( "false", "BOOLEAN" ) );
        assertTrue( !validateVal( "2", "BOOLEAN" ) );
        assertTrue( !validateVal( "-1", "BOOLEAN" ) );
        assertTrue( !validateVal( "string", "BOOLEAN" ) );
        log( "" );
        assertTrue( validateVal( "1", "INT8" ) );
        assertTrue( validateVal( "0", "INT8" ) );
        assertTrue( validateVal( "-1", "INT8" ) );
        assertTrue( validateVal( "127", "INT8" ) );
        assertTrue( validateVal( "-128", "INT8" ) );
        assertTrue( !validateVal( "128", "INT8" ) );
        assertTrue( !validateVal( "-129", "INT8" ) );
        assertTrue( !validateVal( "string", "INT8" ) );
        assertTrue( !validateVal( "22.2", "INT8" ) );
        log( "" );
        assertTrue( validateVal( "32767", "INT16" ) );
        assertTrue( validateVal( "-32768", "INT16" ) );
        assertTrue( !validateVal( "32768", "INT16" ) );
        assertTrue( !validateVal( "-32769", "INT16" ) );
        log( "" );
        assertTrue( validateVal( Integer.toString( Integer.MAX_VALUE ), "INT32" ) );
        assertTrue( validateVal( Integer.toString( Integer.MIN_VALUE ), "INT32" ) );
        assertTrue( !validateVal( "2147483648", "INT32" ) );
        assertTrue( !validateVal( "-2147483649", "INT32" ) );
        log( "" );
        assertTrue( validateVal( Long.toString( Long.MAX_VALUE ), "INT64" ) );
        assertTrue( validateVal( Long.toString( Long.MIN_VALUE ), "INT64" ) );
        assertTrue( !validateVal( "9223372036854775808", "INT64" ) );
        assertTrue( !validateVal( "-9223372036854775809", "INT64" ) );
        log( "" );
        assertTrue( validateVal( "0", "INT8U" ) );
        assertTrue( validateVal( "255", "INT8U" ) );
        assertTrue( !validateVal( "256", "INT8U" ) );
        assertTrue( !validateVal( "-1", "INT8U" ) );
        assertTrue( !validateVal( "-2", "INT8U" ) );
        log( "" );
        assertTrue( validateVal( "0", "INT16U" ) );
        assertTrue( validateVal( "65535", "INT16U" ) );
        assertTrue( !validateVal( "65536", "INT16U" ) );
        assertTrue( !validateVal( "-1", "INT16U" ) );
        assertTrue( !validateVal( "-2", "INT16U" ) );
        log( "" );
        assertTrue( validateVal( "0", "INT32U" ) );
        assertTrue( validateVal( "4294967295", "INT32U" ) );
        assertTrue( !validateVal( "4294967296", "INT32U" ) );
        assertTrue( !validateVal( "-1", "INT32U" ) );
        assertTrue( !validateVal( "-2", "INT32U" ) );
        log( "" );
        assertTrue( validateVal( "0.0", "FLOAT32" ) );
        assertTrue( validateVal( "1.2345", "FLOAT32" ) );
        assertTrue( validateVal( "-1.2345", "FLOAT32" ) );
        assertTrue( validateVal( "100", "FLOAT32" ) );
        assertTrue( validateVal( Float.toString( Float.MAX_VALUE ), "FLOAT32" ) );
        assertTrue( validateVal( Float.toString( -Float.MAX_VALUE ), "FLOAT32" ) );
        assertTrue( !validateVal( "3.4028236E38", "FLOAT32" ) );
        assertTrue( !validateVal( "-3.4028236E38", "FLOAT32" ) );
        assertTrue( !validateVal( "string", "FLOAT32" ) );
        log( "" );
        assertTrue( validateVal( "1234567890123456789012345678901234567890123456789012345678901234", "Octet64" ) );
        assertTrue( !validateVal( "12345678901234567890123456789012345678901234567890123456789012345", "Octet64" ) );
        log( "" );
        assertTrue( validateVal( "1234567890123456789012345678901234567890123456789012345678901234", "VisString64" ) );
        assertTrue(
                !validateVal( "12345678901234567890123456789012345678901234567890123456789012345", "VisString64" ) );
        log( "" );
        assertTrue( validateVal( "1234567890123456789012345678901234567890123456789012345678901234"
                + "12345678901234567890123456789012345678901234567890123456789012345", "VisString129" ) );
        assertTrue( !validateVal( "1234567890123456789012345678901234567890123456789012345678901234"
                + "123456789012345678901234567890123456789012345678901234567890123456", "VisString129" ) );
        log( "" );
        assertTrue( validateVal( "1234567890123456789012345678901234567890123456789012345678901234"
                + "1234567890123456789012345678901234567890123456789012345678901234"
                + "1234567890123456789012345678901234567890123456789012345678901234"
                + "123456789012345678901234567890123456789012345678901234567890123", "VisString255" ) );
        assertTrue( !validateVal( "1234567890123456789012345678901234567890123456789012345678901234"
                + "1234567890123456789012345678901234567890123456789012345678901234"
                + "1234567890123456789012345678901234567890123456789012345678901234"
                + "1234567890123456789012345678901234567890123456789012345678901234", "VisString255" ) );
    }

    private void assertTrue( Boolean b ) {
        if( b ) {
            log( "Check" );
        }
        else {
            log( "Error" );
        }
    }

    private void log( String message ) {
        AbstractRiseClipseConsole.getConsole().info( message );
    }
}