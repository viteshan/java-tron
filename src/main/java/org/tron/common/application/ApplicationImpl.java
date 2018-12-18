package org.tron.common.application;

import com.google.protobuf.ByteString;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.utils.ByteArray;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.db.BlockStore;
import org.tron.core.db.Manager;
import org.tron.core.net.node.Node;
import org.tron.core.net.node.NodeDelegate;
import org.tron.core.net.node.NodeDelegateImpl;
import org.tron.core.net.node.NodeImpl;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.BlockHeader;
import org.tron.protos.Protocol.BlockHeader.raw;

@Slf4j
@Component
public class ApplicationImpl implements Application {

  @Autowired
  private NodeImpl p2pNode;

  private BlockStore blockStoreDb;
  private ServiceContainer services;
  private NodeDelegate nodeDelegate;

  @Autowired
  private Manager dbManager;

  private boolean isProducer;


  private void resetP2PNode() {
    p2pNode.listen();
    p2pNode.syncFrom(null);
  }

  @Override
  public void setOptions(Args args) {
    // not used
  }

  @Override
  @Autowired
  public void init(Args args) {
    blockStoreDb = dbManager.getBlockStore();
    services = new ServiceContainer();
    nodeDelegate = new NodeDelegateImpl(dbManager);
  }

  @Override
  public void addService(Service service) {
    services.add(service);
  }

  @Override
  public void initServices(Args args) {
    services.init(args);
  }

  /**
   * start up the app.
   */
  public void startup() {
    p2pNode.setNodeDelegate(nodeDelegate);
    resetP2PNode();
  }

  @Override
  public void shutdown() {
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    BlockCapsule blockCapsule = new BlockCapsule(Block.newBuilder().setBlockHeader(
      BlockHeader.newBuilder().setRawData(raw.newBuilder().setParentHash(ByteString.copyFrom(
        ByteArray
          .fromHexString("0304f784e4e7bae517bcab94c3e0c9214fb4ac7ff9d7d5a937d1f40031f87b81")))
      )).build());
    for (int i =0; i < 1000000; i ++) {
      executorService.execute(new Runnable() {
        @Override
        public void run() {
          blockStoreDb.put(blockCapsule.getData(), blockCapsule);
        }
      });
      executorService.execute(new Runnable() {
        @Override
        public void run() {
          blockStoreDb.getBlockByLatestNum(4);
        }
      });
    }

    try {
      executorService.wait(1000000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    logger.info("******** begin to shutdown ********");
    synchronized (dbManager.getRevokingStore()) {
      closeRevokingStore();
      closeAllStore();
    }
    closeConnection();
    dbManager.stopRepushThread();
    logger.info("******** end to shutdown ********");
  }

  @Override
  public void startServices() {
    services.start();
  }

  @Override
  public void shutdownServices() {
    services.stop();
  }

  @Override
  public Node getP2pNode() {
    return p2pNode;
  }

  @Override
  public BlockStore getBlockStoreS() {
    return blockStoreDb;
  }

  @Override
  public Manager getDbManager() {
    return dbManager;
  }

  public boolean isProducer() {
    return isProducer;
  }

  public void setIsProducer(boolean producer) {
    isProducer = producer;
  }

  private void closeConnection() {
    logger.info("******** begin to shutdown connection ********");
    try {
      p2pNode.close();
    } catch (Exception e) {
      logger.info("failed to close p2pNode. " + e);
    } finally {
      logger.info("******** end to shutdown connection ********");
    }
  }

  private void closeRevokingStore() {
    dbManager.getRevokingStore().shutdown();
  }

  private void closeAllStore() {
//    if (dbManager.getRevokingStore().getClass() == SnapshotManager.class) {
//      ((SnapshotManager) dbManager.getRevokingStore()).getDbs().forEach(IRevokingDB::close);
//    } else {
//      dbManager.closeAllStore();
//    }
    dbManager.closeAllStore();
  }

}
