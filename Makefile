.PHONY: compile clean-compile test
.PHONY: package build-image publish-release

POM := "./api/pom.xml"
IMAGE_NAME := dms-api

GH_OWNER   := $(shell echo "$(GH_OWNER)" | tr '[:upper:]' '[:lower:]')
GH_REPO    := $(shell echo "$(GH_REPO)" | tr '[:upper:]' '[:lower:]')
REGISTRY   := ghcr.io/$(GH_OWNER)/$(GH_REPO)/$(IMAGE_NAME)
VERSION    ?= latest


clean-compile:
	mvn -f $(POM) clean compile

compile:
	mvn -f $(POM) compile

test: compile
	mvn -f $(POM) test

package:
	.\api\mvnw -f $(POM) clean package

build-image:
	docker build \
		--label "org.opencontainers.image.source=https://github.com/LostChessElo/DMS" \
		-t $(REGISTRY):$(VERSION) \
		-t $(REGISTRY):latest \
		./api

publish-release: build-image
	@echo "$(GH_TOKEN)" | docker login ghcr.io -u $(GH_OWNER) --password-stdin
	docker push $(REGISTRY):$(VERSION)
	docker push $(REGISTRY):latest
	@docker logout ghcr.io
