/*
 * generated by Xtext
 */
package org.opalj.bdl;

/**
 * Initialization support for running Xtext languages 
 * without equinox extension registry
 */
public class BDLStandaloneSetup extends BDLStandaloneSetupGenerated{

	public static void doSetup() {
		new BDLStandaloneSetup().createInjectorAndDoEMFRegistration();
	}
}

