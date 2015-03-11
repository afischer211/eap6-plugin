package com.redhat.plugin.eap6

interface GAV {
	def String getGroupId()
	def String getArtifactId()
	def String getVersion()
	def String getBaseVersion()
}