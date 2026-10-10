package org.waypoints.next.ui;
import org.junit.Test;
import static org.junit.Assert.*;
public class SettingSpecTest {
    @Test public void allDefaultsValidAndNumbersPathsAndFragmentsStrictlyValidated(){
        for(SettingSpec spec:SettingSpec.ALL){assertEquals(spec.defaultValue,spec.validate(spec.defaultValue));
            if(spec.key.equals("waypointDataFile"))reject(spec,"");
            if(spec.key.equals("navigationCartMaximumWaterDepthMetres")){reject(spec,"NaN");reject(spec,"Infinity");}
            if(spec.key.equals("scannerExcludedNames")){reject(spec,new String(new char[81]).replace('\0','a'));}
        }
    }
    private static void reject(SettingSpec spec,String value){try{spec.validate(value);fail("Accepted invalid "+spec.key);}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().startsWith(spec.label));}}
}
