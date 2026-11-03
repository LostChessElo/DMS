.PHONY: compile clean-compile test

POM := "./api/pom.xml"

clean-compile:
	mvn -f $(POM) clean compile

compile:
	mvn -f $(POM) compile

test: compile
	mvn -f $(POM) test
