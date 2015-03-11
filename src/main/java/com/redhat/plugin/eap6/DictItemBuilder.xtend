package com.redhat.plugin.eap6

import java.text.ParseException
import java.io.Reader
import java.io.IOException
import java.util.List
import java.io.BufferedReader
import java.util.ArrayList

class DictItemBuilder {
	def public static DictItem parse(String s) throws ParseException
       {
		var int index = s.indexOf('#');
		var String searchStr = s;
		if (index != -1) // remove comment
			searchStr = searchStr.substring(0, index);
		searchStr = searchStr.trim();
		if (searchStr.length() <= 0)
			return null;

		index = searchStr.indexOf('=');
		if (index == -1)
			throw new ParseException("Expected '=' in " + searchStr, 0);
		val String module = searchStr.substring(index + 1).trim(); // extract the module-name
		searchStr = searchStr.substring(0, index); // extract the maven-coordinates

		val segments = searchStr.split(':').iterator

		val String g = segments.next.trim()
		val String a = segments.next.trim()
		val String v = if(segments.hasNext) segments.next.trim() else "*"

		return new DictItem(g, a, v, if(module.length() == 0) null else module);
	}

	def public static List<DictItem> parse(Reader rd) throws IOException,ParseException {

		var BufferedReader br = if(rd instanceof BufferedReader) rd else new BufferedReader(rd);
		var String line;
		var List<DictItem> list = new ArrayList<DictItem>();
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.length() > 0) {
				val DictItem item = parse(line);
				if (item != null)
					list.add(item);
			}
		}
		return list;
	}
}