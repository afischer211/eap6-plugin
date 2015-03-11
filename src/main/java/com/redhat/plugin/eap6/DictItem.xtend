/*
   Copyright 2013 Red Hat, Inc. and/or its affiliates.

   This file is part of eap6 plugin.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.redhat.plugin.eap6

import org.eclipse.xtend.lib.annotations.Accessors
import org.eclipse.xtend.lib.annotations.Delegate

/**
 * Mapping-Entry from Maven-coordinates g:a:v to an module-name
 */
class DictItem implements GAV{
	@Accessors final String moduleName;
	@Delegate GAV gav;

	public new(String groupId, String artifactId, String version, String moduleName) {
		this.gav=new MavenGAV(groupId,artifactId,version);
		this.moduleName = moduleName;
	}

	override public String toString() {

		val StringBuffer buf = new StringBuffer();
		buf.append('''«gav.groupId»:«gav.artifactId»''');
		if (gav.version != null)
			buf.append(':').append(gav.version);
		buf.append('=');
		if (moduleName != null)
			buf.append(moduleName);
		return buf.toString();
	}
}
