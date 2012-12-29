package com.thoughtworks.i0.gradle.core

class Application {
//    Set<ApplicationFacet> facets = new HashSet<>()
    Map<String, ApplicationFacet> facets = [:]

    def <T extends ApplicationFacet> T find(Class<T> facetClass) {
        def found = facets.values().findAll { facetClass.isInstance(it) }
        if (found.size() == 1) return found.toList().get(0)
        if (found.size() == 0) throw new IllegalArgumentException("No facet with the type: $facetClass")
        throw new IllegalArgumentException("More than one facet with the same type")
    }
}
