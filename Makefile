.PHONY: build test all # always run

build:
	./gradlew clean build dokkaGenerate sourcesJar koverHtmlReport

test:
	./gradlew check

all: build check
