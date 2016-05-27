package com.senseidb.clue.util;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;

/**
 * DocIdSetIterator implementation from a sorted list of integers
 */
public class IntArrayDocIdSetIterator extends DocIdSetIterator{

  private final int[] docids;
  private int docid;
  private int cursor;
  public IntArrayDocIdSetIterator(int[] ids){
    docids = ids;
    reset();
  }

  // used for testing
  public void reset(){
    docid = -1;
    for (cursor = 0;cursor < docids.length;++cursor){
      if (docids[cursor]>=0) {
        break;
      }
    }
    cursor--;
    if (docids.length == 0) {
      docid = NO_MORE_DOCS;
    }
  }

  private final static int binarySearchForNearest(int[] arr,int val, int begin, int end) {
    int mid = (begin + end) >> 1;
    int midval = arr[mid];

    if(mid == end)
      return midval>=val? mid : -1;

    if (midval < val) {
      // Find number equal or greater than the target.
      if (arr[mid + 1] >= val) return mid + 1;

      return binarySearchForNearest(arr,val, mid + 1, end);
    }
    else {
      // Find number equal or greater than the target.
      if (midval == val) return mid;

      return binarySearchForNearest(arr,val, begin, mid);
    }
  }

  @Override
  public int docID() {
    return docid;
  }

  @Override
  public int nextDoc() throws IOException {
    if (cursor < docids.length-1){
      cursor++;
      docid = docids[cursor];
    }
    else{
      docid = NO_MORE_DOCS;
    }
    return docid;
  }

  @Override
  public int advance(int target) throws IOException {
    if (docid != NO_MORE_DOCS){
      if (target < docid) return docid;
      if (target == docid) target = docid+1;
      int end = Math.min(cursor + (target - docid), docids.length-1);
      int idx = binarySearchForNearest(docids,target, cursor + 1, end);
      if (idx!=-1){
        if (cursor < idx){
          cursor = idx;
        }
        else if (cursor == idx){
          cursor++;
        }

        if (cursor>=docids.length){
          docid = NO_MORE_DOCS;
          return docid;
        }
        docid = docids[cursor];
        return docid;
      }
      else{
        cursor = docids.length-1;
        docid = NO_MORE_DOCS;
        return docid;
      }
    }
    else{
      return NO_MORE_DOCS;
    }
  }

  @Override
  public long cost() {
    return docids == null ? 0 : docids.length;
  }
}
