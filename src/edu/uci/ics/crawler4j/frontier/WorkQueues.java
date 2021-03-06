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

package edu.uci.ics.crawler4j.frontier;

import com.sleepycat.je.*;
import edu.uci.ics.crawler4j.url.WebURL;
import edu.uci.ics.crawler4j.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Yasser Ganjisaffar [lastname at gmail dot com]
 */
public class WorkQueues {

  protected Database urlsDB = null;	//key是6字节数据，用于表示先后顺序。value是WebURL
  protected Environment env;

  protected boolean resumable;

  protected WebURLTupleBinding webURLBinding;

  protected final Object mutex = new Object();

  private Logger logger = LoggerFactory.getLogger(WorkQueues.class);

  public WorkQueues(Environment env, String dbName, boolean resumable) throws DatabaseException {
    this.env = env;
    this.resumable = resumable;
    DatabaseConfig dbConfig = new DatabaseConfig();
    dbConfig.setAllowCreate(true);
    dbConfig.setTransactional(resumable);
    dbConfig.setDeferredWrite(!resumable);
    urlsDB = env.openDatabase(null, dbName, dbConfig);
    webURLBinding = new WebURLTupleBinding();
  }

  // 获取数据库中从头开始的max条WebURL的记录
  public List<WebURL> get(int max) throws DatabaseException {
    synchronized (mutex) {
      int matches = 0;
      List<WebURL> results = new ArrayList<>(max);

      Cursor cursor = null;
      OperationStatus result;
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry value = new DatabaseEntry();
      Transaction txn;
      if (resumable) {
        txn = env.beginTransaction(null, null);
      } else {
        txn = null;
      }
      try {
        cursor = urlsDB.openCursor(txn, null);
        result = cursor.getFirst(key, value, null);

        while (matches < max && result == OperationStatus.SUCCESS) {
          if (value.getData().length > 0) {
            results.add(webURLBinding.entryToObject(value));
            matches++;
          }
          result = cursor.getNext(key, value, null);
        }
      } catch (DatabaseException e) {
        if (txn != null) {
          txn.abort();
          txn = null;
        }
        throw e;
      } finally {
        if (cursor != null) {
          cursor.close();
        }
        if (txn != null) {
          txn.commit();
        }
      }
      return results;
    }
  }

  //删除数据库中从头开始的max条WebURL的记录
  public void delete(int count) throws DatabaseException {
    synchronized (mutex) {
      int matches = 0;

      Cursor cursor = null;
      OperationStatus result;
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry value = new DatabaseEntry();
      Transaction txn;
      if (resumable) {
        txn = env.beginTransaction(null, null);
      } else {
        txn = null;
      }
      try {
        cursor = urlsDB.openCursor(txn, null);
        result = cursor.getFirst(key, value, null);

        while (matches < count && result == OperationStatus.SUCCESS) {
          cursor.delete();
          matches++;
          result = cursor.getNext(key, value, null);
        }
      } catch (DatabaseException e) {
        if (txn != null) {
          txn.abort();
          txn = null;
        }
        throw e;
      } finally {
        if (cursor != null) {
          cursor.close();
        }
        if (txn != null) {
          txn.commit();
        }
      }
    }
  }

  /*
   * The key that is used for storing URLs determines the order
   * they are crawled. Lower key values results in earlier crawling.
   * Here our keys are 6 bytes. The first byte comes from the URL priority.
   * The second byte comes from depth of crawl at which this URL is first found.
   * The rest of the 4 bytes come from the docid of the URL. As a result,
   * URLs with lower priority numbers will be crawled earlier. If priority
   * numbers are the same, those found at lower depths will be crawled earlier.
   * If depth is also equal, those found earlier (therefore, smaller docid) will
   * be crawled earlier.
   */
  // 按照priority->depth->docId依次比较爬取顺序
  protected DatabaseEntry getDatabaseEntryKey(WebURL url) {
    byte[] keyData = new byte[6];
    keyData[0] = url.getPriority();
    keyData[1] = (url.getDepth() > Byte.MAX_VALUE ? Byte.MAX_VALUE : (byte) url.getDepth());
    Util.putIntInByteArray(url.getDocid(), keyData, 2);
    return new DatabaseEntry(keyData);
  }

  //插入WebURL记录到合适位置
  public void put(WebURL url) throws DatabaseException {
    DatabaseEntry value = new DatabaseEntry();
    webURLBinding.objectToEntry(url, value);
    Transaction txn;
    if (resumable) {
      txn = env.beginTransaction(null, null);
    } else {
      txn = null;
    }
    urlsDB.put(txn, getDatabaseEntryKey(url), value);
    if (resumable) {
      if (txn != null) {
        txn.commit();
      }
    }
  }

  public long getLength() {
    try {
      return urlsDB.count();
    } catch (Exception e) {
      logger.error("Error in UrlsDB", e);
      return -1;
    }
  }

  public void close() {
    try {
      urlsDB.close();
    } catch (DatabaseException e) {
      logger.error("Error in UrlsDB", e);
    }
  }
}