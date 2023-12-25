# NeoGradle 7 mod locator demo #

A quick demo on one possible way NeoGradle multi-project workspaces could be
improved.

## A quick rundown ##

- `buildSrc` contains a Gradle plugin which generates a special "mod metadata"
  file, which is added as a build artifact using a variant to be shared between
  subprojects in the same root project/workspace
- `modlocator` contains a FML plugin which reads this metadata, and uses it to
  construct mod info to be passed to the mod loader.

## License ##

MIT because I like permissive licenses, but if this concept is merged into
NeoGradle I'm willing to relicense under NeoGradle's license.
