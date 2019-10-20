# ONT-MAP - an OWL2 ontology to ontology data mapper (builder and inference engine).

## Notice
**The activity in this repository is frozen.
The new project's home is https://github.com/owlcs/ont-map**


## Summary
**ONT-MAP** provides a convenient way to build a mapping between two [OWL2](https://www.w3.org/TR/owl2-overview/) ontologies 
and also an inference-engine to transfer and transform the ontological data in accordance with that mapping and OWL2 schemas. 
The mapping can be serialized as a [SPIN](http://spinrdf.org/)-rules RDF file, and, therefore, is [Topbraid Composer](https://www.topquadrant.com/tools/ide-topbraid-composer-maestro-edition/) compatible.

## Motivation
Pure SPIN, which is an RDF-superstructure over SPARQL, is a fairly complex language when it comes to mapping between ontologies, 
and it is incompatible with OWL-centric systems like OWL-API.
**ONT-MAP** offers a way for building mappings without any knowledge about SPIN or SPARQL, 
just only through top-level operations and objects, which can be represented as some high-level pseudo-language, 
that is optimized for GUI developing.

## Dependencies 
 - **[ONT-API](https://github.com/avicomp/ont-api)** the OWL-API implementation on top of Jena
 - **[Topbraid SHACL, ver 1.0.1](https://github.com/TopQuadrant/shacl)** the last public version that supports SPIN
 - [Jena-ARQ, ver 3.x](https://github.com/apache/jena) transitively from ONT-API
 - [OWL-API, ver 5.1.x](https://github.com/owlcs/owlapi) transitively from ONT-API
 
## License
* Apache License Version 2.0

### Notes, propositions and examples
* _ru.avicomp.map.Managers_ is the main class to access to the system.
* An important element of the system is the _ru.avicom.map.MapFunction_ interface, which wraps a SPIN function and is used to map and filter ontology data. It supports varargs and its call can contain an unlimited number of nested calls thus representing a function-chain, and therefore seriously extends functionality. 
* The API provides access only to those spin functions which acceptable with OWL2 model in mapping terms, all other are hidden or rejected. 
Some examples of common reasons to exclude/hide a spin-function:
    - if it interacts with some system resources external to the API (e.g. `smf:lastModified` - function to get the file timestamp). 
    - if it is a part of SPARQL that accepts an iterator (e.g. `sp:notExists` is operator-function to use in SPARQL filter). The API does not allow explicit SPARQL queries as functional parameters.  
    - if it is already implicitly used by the API, but explicitly violates its constraints (e.g. `spinmap:targetResource` is used implicitly to bind several contexts and to create target individuals, in order to control behaviour it is prohibited to use explicitly). 
    - if it is not clear how to handle the function and whether it is worth it (e.g. `spl:primaryKeyURIStart` - a part of a complex and turbid SPIN-API mechanisms, right now I do not see any possibility and necessity to use that functionality. A provided set of functions must be enough to express any mapping between two (or less, or more) OWL2 ontologies with a reasonable schema and data).
    - if it is a property function (i.e. `spin:MagicProperties`). They are allowed only as part of other `MapFunction`s.
    - etc.
* In addition to the standard spin functions (from `spif`, `spinmap`, `fn` and other builtin spin vocabularies) 
there are also ONT-MAP specific functions (such as `avc:UUID`, `avc:currentIndividual`, etc), math functions (`math:log10`, `math:atan2`) and other. 
So, a set of functions that can be used while mapping, has been expanded from one side and narrowed with another. 
But there is no need to to have ONT-MAP libraries for the mapping-instruction to work in Composer - 
all needs are already delivered in the mapping graph itself.
* ONT-MAP is a pluggable system: a set of functions can be expanded by adding extensions. 
There is an example of such an extension: [ont-map-ext-factorial](https://github.com/sszuev/ont-map-ext-factorial).
* All functions (i.e. `MapFunction`s) are supplemented with the complete information about arguments and types 
to be used as elements of constructor in GUI, any inappropriate usage (e.g. incompatible types) causes an error.
* API can work only with OWL2 entities: the context arrow connects two [OWL Class Expressions](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntCE.java), 
to make contexts references [OWL Object Property](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntOPE.java) is used, 
and to map data (make a property bridge in Diagram) [OWL Annotation Property](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntNAP.java) and [OWL Datatype Property](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntNDP.java) are used.
* The mapping inference engine creates [OWL Named Individuals](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntIndividual.java). 
Although, anonymous individuals are theoretically possible, 
currently they are not supported due to SPIN-API limitations related to the target functions.
* There is also a `ru.avicomp.map.ClassPropertyMap`, that is responsible to provide class-properties hierarchical relations, 
which can be used to draw class-boxes with all related properties.
* A simple mapping example with inference:

        // get the manager:
        MapManager manager = Managers.createMapManager();
        // get built-in prefixes:
        PrefixMapping pm = manager.prefixes();
        // get some target-function: 
        MapFunction changeNamespace = manager.getFunction(pm.expandPrefix("spinmapl:changeNamespace"));
        // get the source class from the source schema ontology:
        OntCE sourceClass = source.getOntEntity(OntCE.class, ...);
        // get the target class from the target schema ontology:
        OntCE targetClass = target.getOntEntity(OntCE.class, ...);
        // build target function-call:
        String arg = pm.expandPrefix("spinmapl:targetNamespace");
        MapFunction.Call call = changeNamespace.create().add(arg, "http://example.com#").build();
        // create a mappin-model:
        MapModel mapping = manager.createMapModel();
        // build simple map-context:
        mapping.createContext(sourceClass, targetClass, call);
        // run spin-inference:
        manager.getInferenceEngine(mapping).run(source, target);
        
* A runnable example can be found on ONT-API wiki: https://github.com/avicomp/ont-api/wiki/Examples#ex9        
        
* Printing all supported functions:

        MapManager manager = Managers.createMapManager();
        manager.functions()
                .sorted(Comparator.comparing((MapFunction f) -> !f.isTarget())
                .thenComparing(MapFunction::type)
                .thenComparing(MapFunction::name))
                .forEach(System.out::println);
                
    
