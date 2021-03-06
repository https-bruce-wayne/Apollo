/*
 * Copyright © 2018 Apollo Foundation
 */
package com.apollocurrency.aplwallet.apl.core.app;

import com.apollocurrency.aplwallet.apl.core.app.mint.MintWorker;
import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.db.DatabaseManager;
import com.apollocurrency.aplwallet.apl.core.peer.PeersService;
import com.apollocurrency.aplwallet.apl.core.task.TaskDispatchManager;
import com.apollocurrency.aplwallet.apl.util.env.RuntimeEnvironment;
import com.apollocurrency.aplwallet.apl.util.env.RuntimeMode;
import com.apollocurrency.aplwallet.apl.util.env.RuntimeParams;
import com.apollocurrency.aplwallet.apl.util.injectable.PropertiesHolder;
import com.apollocurrency.aplwallet.apl.util.task.Task;
import com.apollocurrency.aplwallet.apl.util.task.TaskDispatcher;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.spi.CDI;
import javax.inject.Singleton;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

import static com.apollocurrency.aplwallet.apl.util.Constants.HEALTH_CHECK_INTERVAL;

/**
 * Runtime environment for AplCores (singleton) TODO: make it injectable
 * singleton
 *
 * @author alukin@gmail.com
 */
@Singleton
public class AplCoreRuntime {
    //probably it is temprary solution, we should move WebUI serving out of core

    private static final Logger LOG = LoggerFactory.getLogger(AplCoreRuntime.class);
    private final List<AplCore> cores = new ArrayList<>();
    private final DatabaseManager databaseManager;
    private final AplAppStatus aplAppStatus;
    private final PeersService peers;
    private RuntimeMode runtimeMode;
    //TODO: may be it is better to take below variables from here instead of getting it from CDI
    // in every class?
    private BlockchainConfig blockchainConfig;
    private PropertiesHolder propertiesHolder;
    //TODO:  check and debug minting
    private MintWorker mintworker;
    private Thread mintworkerThread;


    private TaskDispatchManager taskManager;

    // WE CAN'T use @Inject here for 'RuntimeMode' instance because it has several candidates (in CDI hierarchy)
    public AplCoreRuntime() {
        this.databaseManager = CDI.current().select(DatabaseManager.class).get();
        this.aplAppStatus = CDI.current().select(AplAppStatus.class).get();
        this.peers = CDI.current().select(PeersService.class).get();

    }

    public static void logSystemProperties() {
        String[] loggedProperties = new String[]{
            "java.version",
            "java.vm.version",
            "java.vm.name",
            "java.vendor",
            "java.vm.vendor",
            "java.home",
            "java.library.path",
            "java.class.path",
            "os.arch",
            "sun.arch.data.model",
            "os.name",
            "file.encoding",
            "java.security.policy",
            "java.security.manager",
            RuntimeEnvironment.RUNTIME_MODE_ARG,};
        for (String property : loggedProperties) {
            LOG.debug("{} = {}", property, System.getProperty(property));
        }
        LOG.debug("availableProcessors = {}", Runtime.getRuntime().availableProcessors());
        LOG.debug("maxMemory = {}", Runtime.getRuntime().maxMemory());
        LOG.debug("processId = {}", RuntimeParams.getProcessId());
    }

    public void init(RuntimeMode runtimeMode, BlockchainConfig blockchainConfig, PropertiesHolder propertiesHolder, TaskDispatchManager taskManager) {
        this.blockchainConfig = blockchainConfig;
        this.propertiesHolder = propertiesHolder;
        this.runtimeMode = runtimeMode;
        this.taskManager = taskManager;
        TaskDispatcher taskDispatcher = taskManager.newScheduledDispatcher("AplCoreRuntime-periodics");
        taskDispatcher.schedule(Task.builder()
            .name("Core-health")
            .initialDelay(HEALTH_CHECK_INTERVAL * 2)
            .delay(HEALTH_CHECK_INTERVAL)
            .task(() -> {
                LOG.info(getNodeHealth());
                aplAppStatus.clearFinished(1 * 60L); //10 min
            })
            .build());
    }

    private void findDeadLocks(StringBuilder sb) {
        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        long[] ids = tmx.findDeadlockedThreads();
        if (ids != null) {
            // threads that are in deadlock waiting to acquire object monitors or ownable synchronizers
            sb.append("DeadLocked threads found:\n");
            printDeadLockedThreadInfo(sb, tmx, ids);
        } else if (tmx.findMonitorDeadlockedThreads() != null) {
            //threads that are blocked waiting to enter a synchronization block or waiting to reenter a synchronization block
            sb.append("Monitor DeadLocked threads found:\n");
            printDeadLockedThreadInfo(sb, tmx, tmx.findMonitorDeadlockedThreads());
        } else {
            sb.append("\nNo dead-locked threads found.\n");
        }
    }

    private void printDeadLockedThreadInfo(StringBuilder sb, ThreadMXBean tmx, long[] ids) {
        ThreadInfo[] infos = tmx.getThreadInfo(ids, true, true);
        sb.append("Following Threads are deadlocked:\n");
        for (ThreadInfo info : infos) {
            sb.append(info.toString()).append("\n");
        }
    }

    private String getNodeHealth() {
        StringBuilder sb = new StringBuilder("Node health info\n");
        HikariPoolMXBean jmxBean = databaseManager.getDataSource().getJmxBean();
        String usedConnections = null;
        if (jmxBean != null) {
            int totalConnections = jmxBean.getTotalConnections();
            int activeConnections = jmxBean.getActiveConnections();
            int idleConnections = jmxBean.getIdleConnections();
            int threadAwaitingConnections = jmxBean.getThreadsAwaitingConnection();
            usedConnections = String.format("Total/Active/Idle connections in Pool '%d'/'%d'/'%d', threadsAwaitPool=[%d], 'main-db'",
                totalConnections,
                activeConnections,
                idleConnections,
                threadAwaitingConnections);
        }
        sb.append("Used DB connections: ").append(usedConnections);
        Runtime runtime = Runtime.getRuntime();
        sb.append("\nRuntime total memory :").append(String.format(" %,d KB", (runtime.totalMemory() / 1024)));
        sb.append("\nRuntime free  memory :").append(String.format(" %,d KB", (runtime.freeMemory() / 1024)));
        sb.append("\nRuntime max   memory :").append(String.format(" %,d KB", (runtime.maxMemory() / 1024)));
        sb.append("\nActive threads count :").append(Thread.currentThread().getThreadGroup().getParent().activeCount());
        sb.append("\nInbound peers count: ").append(peers.getInboundPeers().size());
        sb.append(", Active peers count: ").append(peers.getActivePeers().size());
        sb.append(", Known peers count: ").append(peers.getAllPeers().size());
        sb.append(", Connectable peers count: ").append(peers.getAllConnectablePeers().size());
        findDeadLocks(sb);
        return sb.toString();
    }

    public void addCoreAndInit() {
        AplCore core = CDI.current().select(AplCore.class).get();
        addCore(core);
        core.init();
    }

    public void addCore(AplCore core) {
        cores.add(core);
    }

    public void shutdown() {
        for (AplCore c : cores) {
            c.shutdown();
        }
        if (mintworker != null) {
            mintworker.stop();
            try {
                mintworkerThread.join(200);
            } catch (InterruptedException ex) {
            }
        }
        runtimeMode.shutdown();
    }

    public void startMinter() {
        mintworker = new MintWorker(propertiesHolder, blockchainConfig);
        mintworkerThread = new Thread(mintworker);
        mintworkerThread.setDaemon(true);
        mintworkerThread.start();
    }

    public void stopMinter() {
        if (mintworker != null) {
            mintworker.stop();
        }
    }

}
