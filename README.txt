=== Intro ===

This is a plugin to transfer Twitter tweets (search requests and user timelines) to the Sone freenet plugin.

=== INSTALL ===

1) Build the plugin using the ant buildfile  (requires editing for specifying the main freenet jars)
2) Enable the FCPInterface in Sone
3) Load the plugin in Freenet
4) Go to http://FREENET_HOST:8888/SoneBridge/setup
5) Setup oAUTH by following the instructions on the setup page (basically get an OOB, enter it and press SUBMIT)
6) Go to http://FREENET_HOST:8888/Sonebridge/manage to manage mapping a Twitter search request to a Sone ID

Configuration settings are stored in the main Freenet folder in the file: SoneBridge.json
At present it checks twitter every 60 seconds.

WARNING: This is an initial alpha, no guarantees of any kind.
LICENSE: GPLv3
