/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.topbraid.spin.util;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shared.Lock;
import org.apache.jena.sparql.core.DatasetGraphBase;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.util.Context;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A copy-paste in order to provide correct org.apache.jena.sparql.util.Context object and an upgrade to work with jena 3.7.0.
 * WARNING: need to make sure that this class goes before in classpath or replaces the original (topbraid) class.
 * To achieve this we use maven-dependency-plugin.
 * TODO: replace with <a href='https://github.com/spinrdf/spinrdf'>org.spinrdf:spinrdf</a> when it is available.
 * <p>
 * An implementation of DatasetGraph that delegates all work to a given Dataset implementation.
 *
 * @author Holger Knublauch
 * Created by @szuev on 18.06.2018.
 */
public class DatasetWrappingDatasetGraph extends DatasetGraphBase {

    private Dataset dataset;

    public DatasetWrappingDatasetGraph(Dataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public void add(Quad quad) {
        Graph graph = getGraph(quad);
        if (graph != null) {
            graph.add(quad.asTriple());
        }
    }

    @Override
    public boolean containsGraph(Node graphNode) {
        return dataset.containsNamedModel(graphNode.getURI());
    }

    @Override
    public void delete(Quad quad) {
        Graph graph = getGraph(quad);
        if (graph != null) {
            graph.delete(quad.asTriple());
        }
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Iterator<Node> listGraphNodes() {
        List<Node> results = new LinkedList<>();
        Iterator<String> names = dataset.listNames();
        while (names.hasNext()) {
            String name = names.next();
            results.add(NodeFactory.createURI(name));
        }
        return results.iterator();
    }

    @Override
    public Iterator<Quad> find(Node g, Node s, Node p, Node o) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Quad> findNG(Node g, Node s, Node p, Node o) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException();
    }

    @Override
    public Graph getDefaultGraph() {
        Model defaultModel = dataset.getDefaultModel();
        if (defaultModel != null) {
            return defaultModel.getGraph();
        } else {
            return null;
        }
    }

    @Override
    public Graph getGraph(Node graphNode) {
        try {
            Model model = dataset.getNamedModel(graphNode.getURI());
            if (model != null) {
                return model.getGraph();
            } else {
                return null;
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Exception accessing named graph " + graphNode, ex);
        }
    }

    protected Graph getGraph(Quad quad) {
        if (quad.isDefaultGraph()) {
            return getDefaultGraph();
        } else {
            return getGraph(quad.getGraph());
        }
    }

    @Override
    public Lock getLock() {
        return dataset.getLock();
    }

    @Override
    public Context getContext() {
        return dataset.getContext();
    }

    @Override
    public long size() {
        int count = 0;
        Iterator<Node> it = listGraphNodes();
        while (it.hasNext()) {
            it.next();
            count++;
        }
        return count;
    }

    @Override
    public void addGraph(Node graphName, Graph graph) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeGraph(Node graphName) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException();
    }

    @Override
    public void begin(TxnType type) {
        dataset.begin(type);
    }

    @Override
    public void begin(ReadWrite readWrite) {
        dataset.begin(readWrite);
    }

    @Override
    public boolean promote(Promote mode) {
        return false;
    }

    @Override
    public ReadWrite transactionMode() {
        return dataset.transactionMode();
    }

    @Override
    public TxnType transactionType() {
        return dataset.transactionType();
    }

    @Override
    public void commit() {
        dataset.commit();
    }

    @Override
    public void abort() {
        dataset.abort();
    }

    @Override
    public boolean isInTransaction() {
        return dataset.isInTransaction();
    }

    @Override
    public void end() {
        dataset.end();
    }

    @Override
    public boolean supportsTransactions() {
        return dataset.supportsTransactions();
    }
}

