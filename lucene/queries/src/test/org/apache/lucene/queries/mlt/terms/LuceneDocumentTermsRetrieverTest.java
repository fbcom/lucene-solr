/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.queries.mlt.terms;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.mlt.MoreLikeThisParameters;
import org.apache.lucene.queries.mlt.MoreLikeThisTestBase;
import org.apache.lucene.queries.mlt.terms.scorer.ScoredTerm;
import org.apache.lucene.util.PriorityQueue;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;

public class LuceneDocumentTermsRetrieverTest extends InterestingTermsRetrieverTestBase {
  private LuceneDocumentTermsRetriever toTest;

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  protected PriorityQueue<ScoredTerm> retrieveScoredTerms(MoreLikeThisParameters params) throws IOException {
    initIndex();
    Document testDocument = getDocumentWithLinearTermFrequencies(numDocs);
    toTest = new LuceneDocumentTermsRetriever(reader);
    toTest.setParameters(params);
    return toTest.retrieveTermsFromDocument(testDocument);
  }

}

