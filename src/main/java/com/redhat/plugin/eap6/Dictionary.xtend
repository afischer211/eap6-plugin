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

import java.util.ArrayList
import java.util.List
import org.apache.maven.plugin.logging.Log
import org.eclipse.xtend.lib.annotations.Accessors

/**
 * Manages entries from one config-file
 */
class Dictionary {
	@Accessors final private List<DictItem> dictionary = new ArrayList<DictItem>();
	@Accessors final private String name;

	new(String name, List<DictItem> dict) {
		this.name=name;
		for (DictItem item : dict) {
			dictionary.add(item);
		}
	}

	new(List<DictItem> dict) {
		this(null,dict)
	}

	def DictItem find(Log logger, MavenGAV gav) {
		return find(logger,gav.groupId,gav.artifactId,gav.version)
	}

	/**
     * Finds the best matching artifact mapping
     */
	def DictItem find(Log logger, String groupId, String artifactId, String version) {
		var DictItem match = null;

		//logger.info('''Look for <«groupId»:«artifactId»:«version»>''')
		for (DictItem item : dictionary) {
			if (item.groupId.equals(groupId) && item.artifactId.equals(artifactId)) {

				// If there is a version matching this version, pick that
				if (version.equals(item.version)) {
					match = item;
					logger.debug('''«name»: Found matching item <«item.toString()»>''');
				} else {
					if (item.version.equals("*")) { // Version is not important
						if (match != null && match.version.equals("*")) {
							throw new RuntimeException("Duplicate:" + match);
						}
						if (match == null)
							match = item;
					} else {

						// deal with SNAPSHOT-versions
						val itemBaseVersion = item.getBaseVersion()
						val baseVersion = MavenGAV.extractBaseVersion(version)
						//logger.info('''itemBase:<«itemBaseVersion»> base:<«baseVersion»>''')
						if (baseVersion.equals(itemBaseVersion)) {
							match = item;
							logger.debug('''«name»: Found matching snapshot-item <«item.toString()»>''');
						} else {
							//item.logger.warn('''mismatching version <«item.version»:«version»>''')
						}
					}
				}
			}
		}
		return match;
	}
}
