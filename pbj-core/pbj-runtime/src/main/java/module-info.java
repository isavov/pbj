/**
 * Runtime module of code needed by PBJ generated code at runtime.
 */
module com.hedera.pbj.runtime {
    requires static com.github.spotbugs.annotations;
	exports com.hedera.pbj.runtime;
    exports com.hedera.pbj.runtime.test;
    exports com.hedera.pbj.runtime.io;
}