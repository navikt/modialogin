.PHONY: all clean build start test stop

all: clean build start test stop

clean:
	./gradlew clean

build:
	./gradlew build

start:
	docker-compose up --build -d

stop:
	docker-compose down --remove-orphans

test:
	node test/test.js
