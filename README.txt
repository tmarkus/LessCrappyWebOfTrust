A WebOfTrust plugin for freenet.

Currently in a very early pre-alpha version and far from feature-complete.

Requires https://github.com/tmarkus/GraphDatabase in lib/


## Building

cd $GraphDB
ant clean ; ant

cd $lcwot
cp $GraphDB/dist/h2graph.jar lib/
ant clean ; ant


## Using

Add the created jar-file as unofficial plugin: http://127.0.0.1:8888/plugins/


## Retrieving over Freenet

For retrieving over Freenet, you can use the following infocalypse repositories¹:

USK@j8ZTiCcW7NCkmFXpkCh0jQfj~RIwJC41msLI6-COaNo,Xo72DhRuVKSH2SMm~PH3Jh1hE7MnSTC45Ld9vv7vkho,AQACAAE/lcwot.R1/5
USK@j8ZTiCcW7NCkmFXpkCh0jQfj~RIwJC41msLI6-COaNo,Xo72DhRuVKSH2SMm~PH3Jh1hE7MnSTC45Ld9vv7vkho,AQACAAE/graphdatabase.R1/3

¹: Infocalypse: 
   USK@-bk9znYylSCOEDuSWAvo5m72nUeMxKkDmH3nIqAeI-0,qfu5H3FZsZ-5rfNBY-jQHS5Ke7AT2PtJWd13IrPZjcg,AQACAAE/feral_codewright/25/infocalypse_howto.html
