package ru.avicomp.map.tools;

import org.apache.jena.graph.Factory;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.Context;
import ru.avicomp.map.spin.vocabulary.*;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An utility class to produce avc.spin.ttl (see resources/etc directory).
 * For developing and demonstration.
 * NOTE: Not a part of API or APIs Tests: will be removed.
 * <p>
 * Created by @szuev on 07.04.2018.
 *
 * @see AVC
 * @see ARQ
 * @see FN
 */
public class AVCLibraryMaker {

    public static void main(String... args) {
        OntGraphModel m = LibraryMaker.createModel(Factory.createGraphMem());
        OntID id = m.setID(AVC.BASE_URI);
        id.setVersionIRI(AVC.NS + "1.0");
        id.addComment("A library that contains basic definitions required by ONT-MAP API.\n" +
                "Also it is an addition to the standard spin-family in order to customize functions behaviour.", null);
        id.addAnnotation(m.getAnnotationProperty(OWL.versionInfo), "version 1.0", null);
        // depends on spinmap to reuse variables (spinmap:_source, spin:_this, spin:_arg*) while building functions bodies
        id.addImport(SPINMAPL.BASE_URI);

        OntDT xsdString = m.getOntEntity(OntDT.class, XSD.xstring);

        OntNDP hidden = m.createOntEntity(OntNDP.class, AVC.hidden.getURI());
        hidden.addRange(xsdString);
        hidden.addComment("A property for marking unused functions from standard spin-map library supply.", null);

        OntNDP runtime = m.createOntEntity(OntNDP.class, AVC.runtime.getURI());
        runtime.addRange(xsdString);
        runtime.addComment("A property for using to describe runtime functionality provided by ONT-MAP API", null);

        // any rdf-node datatype
        m.createOntEntity(OntDT.class, AVC.undefined.getURI()).addProperty(RDFS.comment, "Any RDF Node, i.e. either resource or literal");

        // numeric datatype (xs:numeric)
        OntDT numeric = m.createOntEntity(OntDT.class, AVC.numeric.getURI());
        numeric.addComment("Represents all numeric datatypes", null);
        numeric.addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/sparql11-query/#operandDataTypes"));
        List<OntDR> numberDRs = Stream.of(XSD.integer, XSD.decimal, XSD.xfloat, XSD.xdouble,
                XSD.nonPositiveInteger, XSD.negativeInteger,
                XSD.nonNegativeInteger, XSD.positiveInteger,
                XSD.xlong, XSD.xint, XSD.xshort, XSD.xbyte,
                XSD.unsignedLong, XSD.unsignedInt, XSD.unsignedShort, XSD.unsignedByte).map(r -> m.getOntEntity(OntDT.class, r)).collect(Collectors.toList());
        numeric.addEquivalentClass(m.createUnionOfDataRange(numberDRs));

        // AVC:MagicFunctions
        AVC.MagicFunctions.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPIN.Functions)
                .addProperty(SPIN.abstract_, Models.TRUE)
                .addProperty(RDFS.label, "Magic functions")
                .addProperty(RDFS.comment, "A special collection of functions provided by AVC that require special treatment while inference\n" +
                        "and therefore may not work as expected in Topbraid Composer.");

        // AVC:AggregateFunctions
        AVC.AggregateFunctions.inModel(m)
                .addProperty(RDF.type, SPIN.Function)
                .addProperty(RDFS.subClassOf, SPIN.Functions)
                .addProperty(SPIN.abstract_, Models.TRUE)
                .addProperty(RDFS.label, "Aggregate functions")
                .addProperty(RDFS.comment,
                        "A collection of functions that uses SPARQL aggregate functionality (i.e. COUNT, SUM, MIN, MAX, GROUP_CONCAT).");

        // Customize mathematical functions:
        // SP:abs
        SP.resource("abs").inModel(m).addProperty(hidden,
                "Duplicates the function fn:abs, which is preferable, since it has information about return types.");
        // SP:max & SP:min can handle also xsd:string since it is SPARQL which is used overloaded operators ">" and "<"
        Stream.of(SPL.max, SPL.min).map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(RDFS.comment, "Can work both with numeric datatypes and xsd:string.")
                        .addProperty(RDFS.subClassOf, SPL.StringFunctions));
        // SP:sub can accept any numbers
        SP.sub.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions/#func-numeric-subtract"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric))
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, AVC.numeric));
        // SP:add can handle string also since it is SPIN representation of SPARQL "+", which is overloaded
        SP.add.inModel(m).addProperty(RDFS.comment, "Can work both with numeric datatypes and xsd:string.")
                .addProperty(RDFS.subClassOf, SPL.StringFunctions)
                .addProperty(AVC.returnType, RDFS.Literal)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, RDFS.Literal))
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, RDFS.Literal));
        // SP:divide
        SP.divide.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions/#func-numeric-divide"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric))
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, AVC.numeric));
        // SP:mul can accept only numeric
        SP.mul.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions/#func-numeric-multiply"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric))
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, AVC.numeric));
        // SP:unaryMinus & SP:unaryPlus
        SP.unaryPlus.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions/#func-numeric-unary-plus"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric));
        SP.unaryMinus.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions/#func-numeric-unary-minus"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric));
        // SP:ceil & SP:floor & SP:round
        Stream.of(SP.ceil, SP.floor, SP.round).map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(AVC.returnType, AVC.numeric)
                        .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric)));

        // Boolean functions:
        SP.isNumeric.inModel(m).addProperty(AVC.returnType, XSD.xboolean);

        // Datatime functions:
        SP.tz.inModel(m)
                .addProperty(AVC.returnType, XSD.xstring)
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg1)
                        .addProperty(SPL.valueType, XSD.dateTime));

        // todo: hide SPIF:random in favour of SP:rand?

        // SP:datatype
        SP.resource("datatype").inModel(m).addProperty(AVC.returnType, RDFS.Datatype);

        // SP:eq can accept any resource, not only boolean literals
        SP.eq.inModel(m)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.undefined))
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, AVC.undefined));

        // ARQ(prefix=afn) functions: AFN:max and AFN:min can handle only numeric
        Stream.of(ARQ.max, ARQ.min).map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(AVC.returnType, AVC.numeric)
                        .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric))
                        .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg2, AVC.numeric)));
        // ARQ:pi replaced with MATH:pi
        ARQ.resource("pi").inModel(m).addProperty(AVC.hidden, "Use math:pi instead");

        // SPINMAP:targetResource
        SPINMAP.targetResource.inModel(m)
                .addProperty(SPIN.private_, Models.TRUE)
                .addProperty(hidden, "This function should not be allowed to be used explicitly by API.\n" +
                        "All cases when spinmap:targetResource might be used, should be described through other functions.");

        // SPINMAPL:relatedSubjectContext
        SPINMAPL.relatedSubjectContext.inModel(m)
                .addProperty(hidden, "Instead of explicit calling this function, please use " + Context.class.getName() +
                        "#createRelatedContext(...) methods.");
        // SPINMAPL:relatedObjectContext
        SPINMAPL.relatedObjectContext.inModel(m)
                .addProperty(hidden, "Instead of explicit calling this function, please use " + Context.class.getName() +
                        "#createRelatedContext(...) methods.");

        // Exclude SPIN primary key functionality
        Stream.of(SPINMAPL.resource("resourceWithPrimaryKey"),
                SPINMAPL.resource("usePrimaryKey"),
                SPL.resource("primaryKeyProperty"),
                SPL.resource("primaryKeyURIStart"),
                SPL.resource("hasPrimaryKey"),
                SPL.resource("isPrimaryKeyPropertyOfInstance"))
                .map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(hidden, "Primary-key functionality is excluded since it is not compatible with ONT-MAP logic"));
        // Exclude functions affecting spin:Module, spin:Function, sp:Query as not compatible with this API
        Stream.of(SPL.resource("hasArgument"),
                SPIF.resource("convertSPINRDFToString"),
                SPIF.resource("invoke"),
                SPIF.resource("canInvoke"),
                SPIF.resource("walkObjects"),
                SPIN.resource("ask")).map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(hidden, "Functions accepting or returning spin:Module or sp:Query are not compatible with ONT-MAP logic"));
        // SPIN:violatesConstraints - where to use it?
        SPIN.violatesConstraints.inModel(m).addProperty(hidden, "This function is not compatible with OWL2 world");
        // SP:coalesce, SP:exists, SP:notExists
        Stream.of("coalesce", "exists", "notExists").map(SP::resource).map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(hidden, "Part of SPARQL, which cannot be used explicitly in ONT-MAP"));
        // SPIF:buildStringFromRDFList, SPIF:hasAllObjects
        Stream.of(SPIF.resource("buildStringFromRDFList"), SPIF.resource("hasAllObjects")).map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(hidden, "Hidden: OWL2 does not support custom rdf:List"));

        // varargs:
        SP.resource("concat").inModel(m).addProperty(SPIN.constraint, m.createResource()
                .addProperty(RDF.type, SPL.Argument)
                .addProperty(SPL.predicate, AVC.vararg)
                .addProperty(SPL.valueType, XSD.xstring));
        Stream.of("in", "notIn")
                .map(SP::resource)
                .map(r -> r.inModel(m))
                .forEach(r -> r.addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, AVC.vararg)
                        .addProperty(SPL.valueType, RDFS.Literal)));

        // FN:abs takes a number, not any literal
        FN.abs.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/TR/xpath-functions-31/#func-abs"))
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric));

        // FN:round
        FN.round.inModel(m)
                .addProperty(RDFS.seeAlso, m.getResource("https://www.w3.org/2005/xpath-functions/#round"))
                .addProperty(RDFS.comment, "Rounds a value to a specified number of decimal places, rounding upwards if two such values are equally near.")
                .addProperty(AVC.returnType, AVC.numeric)
                .addProperty(AVC.constraint, LibraryMaker.createConstraint(m, SP.arg1, AVC.numeric))
                .addProperty(SPIN.constraint, m.createResource()
                        .addProperty(RDF.type, SPL.Argument)
                        .addProperty(SPL.predicate, SP.arg2)
                        .addProperty(SPL.valueType, XSD.integer)
                        .addProperty(SPL.optional, Models.TRUE)
                        .addProperty(RDFS.comment, "The precision, int"));

        m.write(System.out, "ttl");
    }

}
