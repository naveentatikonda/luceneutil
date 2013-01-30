package perf;

/**
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

import java.io.IOException;
import java.util.List;

import org.apache.lucene.facet.index.params.CategoryListParams.OrdinalPolicy;
import org.apache.lucene.facet.index.params.CategoryListParams;
import org.apache.lucene.facet.index.params.FacetIndexingParams;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.postingshighlight.PostingsHighlighter;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.apache.lucene.search.vectorhighlight.FastVectorHighlighter;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.RamUsageEstimator;

class IndexState {
  public final ReferenceManager<IndexSearcher> mgr;
  public final DirectSpellChecker spellChecker;
  public final Filter groupEndFilter;
  public final FastVectorHighlighter fastHighlighter;
  public final boolean useHighlighter;
  public final PostingsHighlighter postingsHighlighter;
  public final String textFieldName;
  public int[] docIDToID;
  public final boolean hasDeletions;
  public final TaxonomyReader taxoReader;
  public final FacetIndexingParams iParams;

  public IndexState(ReferenceManager<IndexSearcher> mgr, TaxonomyReader taxoReader, String textFieldName, DirectSpellChecker spellChecker, String hiliteImpl) throws IOException {
    this.mgr = mgr;
    this.spellChecker = spellChecker;
    this.textFieldName = textFieldName;
    this.taxoReader = taxoReader;
    CategoryListParams clp = new CategoryListParams() {
        /*
        @Override
        public IntEncoder createEncoder() {
          return new SortingIntEncoder(new UniqueValuesIntEncoder(new DGapIntEncoder(new PackedIntEncoder())));
        }
        */

        @Override
        public OrdinalPolicy getOrdinalPolicy(String field) {
          return OrdinalPolicy.NO_PARENTS;
        }
      };
    iParams = new FacetIndexingParams(clp);
    //iParams = new FacetIndexingParams();
    /*
      iParams = new FacetIndexingParams() {
          // nocommit
          @Override
          public OrdinalPolicy getOrdinalPolicy() {
            return OrdinalPolicy.NO_PARENTS;
          }
        };
    */
    
    groupEndFilter = new CachingWrapperFilter(new QueryWrapperFilter(new TermQuery(new Term("groupend", "x"))));
    if (hiliteImpl.equals("FastVectorHighlighter")) {
      fastHighlighter = new FastVectorHighlighter(true, true);
      useHighlighter = false;
      postingsHighlighter = null;
    } else if (hiliteImpl.equals("PostingsHighlighter")) {
      fastHighlighter = null;
      useHighlighter = false;
      postingsHighlighter = new PostingsHighlighter();
    } else if (hiliteImpl.equals("Highlighter")) {
      fastHighlighter = null;
      useHighlighter = true;
      postingsHighlighter = null;
    } else {
      throw new IllegalArgumentException("unrecognized -hiliteImpl \"" + hiliteImpl + "\"");
    }
    IndexSearcher searcher = mgr.acquire();
    try {
      hasDeletions = searcher.getIndexReader().hasDeletions();
      /*
      if (taxoReader != null) {
        // nocommit doesn't work w/ NRT?
        clCache.loadAndRegister(clp, searcher.getIndexReader(), taxoReader, iParams);
        System.out.println("FACETS CACHE SIZE: " + RamUsageEstimator.sizeOf(clCache));
      }
      */
    } finally {
      mgr.release(searcher);
    }
  }

  public void setDocIDToID() throws IOException {
    IndexSearcher searcher = mgr.acquire();
    try {
      docIDToID = new int[searcher.getIndexReader().maxDoc()];
      int base = 0;
      for(AtomicReaderContext sub : searcher.getIndexReader().leaves()) {
        final int[] ids = FieldCache.DEFAULT.getInts(sub.reader(), "id", new FieldCache.IntParser() {
            @Override
            public int parseInt(BytesRef term) {
              return LineFileDocs.idToInt(term);
            }
          }, false);
        System.arraycopy(ids, 0, docIDToID, base, ids.length);
        base += ids.length;
      }
    } finally {
      mgr.release(searcher);
    }
  }
}
