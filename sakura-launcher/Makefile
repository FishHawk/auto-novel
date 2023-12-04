version := $(shell cat VERSION)
all:
	echo $(version)
	mv src sakura-launcher.$(version)
	zip -r sakura-launcher.$(version).zip sakura-launcher.$(version)
	mv sakura-launcher.$(version) src