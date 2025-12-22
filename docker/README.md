# Docker Image

This Docker setup packages the Clue CLI and web server into a single image.
It builds the Gradle artifacts and includes `bin/`, `config/`, and `build/libs`.

## Build

```sh
docker build -f docker/Dockerfile -t clue .
```

## Run the CLI

```sh
docker run --rm -it clue clue.sh <index> [command args]
```

Example:

```sh
docker run --rm -it clue clue.sh /data/index search "electric"
```

## Run the web server

```sh
docker run --rm -p 8080:8080 clue clue-web.sh
```

## Plugins

Plugin jars can be mounted into `/opt/clue/plugins` and are added to the classpath
by `clue.sh` and `clue-web.sh`.

```sh
docker run --rm -it -v "$PWD/plugins:/opt/clue/plugins" clue clue.sh <index> [command args]
```

```sh
docker run --rm -p 8080:8080 -v "$PWD/plugins:/opt/clue/plugins" clue clue-web.sh
```

## Publish a release image

Log in to GitHub Container Registry, build the image with a version tag, and push
it. Replace `<owner>` and `<version>` with your values.

```sh
echo "$GITHUB_TOKEN" | docker login ghcr.io -u "<owner>" --password-stdin
docker build -f docker/Dockerfile -t ghcr.io/<owner>/clue:<version> .
docker push ghcr.io/<owner>/clue:<version>
```
