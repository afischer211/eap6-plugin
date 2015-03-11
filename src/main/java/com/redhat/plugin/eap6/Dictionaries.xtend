package com.redhat.plugin.eap6

import java.util.List
import org.eclipse.xtend.lib.annotations.Accessors
import java.util.ArrayList
import java.io.FileReader
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.text.ParseException
import java.io.InputStreamReader
import org.apache.maven.plugin.logging.Log

class Dictionaries {
	@Accessors final private List<Dictionary> dictionaries = new ArrayList<Dictionary>()

	def public void addDictionary(Dictionary dict) {
		dictionaries.add(dict);
	}

	def public void addDictionary(File f) throws IOException, ParseException {
		val FileReader reader = new FileReader(f);
		addDictionary(new Dictionary(f.name,DictItemBuilder.parse(reader)));
	}

	def public void addDictionary(InputStream stream) throws IOException, ParseException {
		addDictionary(new Dictionary(DictItemBuilder.parse(new InputStreamReader(stream))));
	}

	def public DictItem find(Log logger, String groupId, String artifactId, String version) {

		// reverse lookup through all readed dictionaries
		for (var int n = dictionaries.size() - 1; n >= 0; n--) {
			val Dictionary dict = dictionaries.get(n);
			val DictItem item = dict.find(logger, groupId, artifactId, version);
			if (item != null)
				return item;
		}
		return null;
	}
}
