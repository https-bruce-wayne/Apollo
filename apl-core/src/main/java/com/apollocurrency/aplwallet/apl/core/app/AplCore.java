/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

/*
 * Copyright © 2018 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.app;


import com.apollocurrency.aplwallet.apl.util.cdi.AplContainer;
import com.apollocurrency.aplwallet.apl.core.addons.AddOns;
import com.apollocurrency.aplwallet.apl.core.chainid.ChainIdDbMigrator;
import com.apollocurrency.aplwallet.apl.core.chainid.ChainIdService;
import com.apollocurrency.aplwallet.apl.core.chainid.DbInfoExtractor;
import com.apollocurrency.aplwallet.apl.core.chainid.DbMigrator;
import com.apollocurrency.aplwallet.apl.core.chainid.H2DbInfoExtractor;
import com.apollocurrency.aplwallet.apl.core.db.FullTextTrigger;
import com.apollocurrency.aplwallet.apl.core.db.model.Option;
import com.apollocurrency.aplwallet.apl.core.http.API;
import com.apollocurrency.aplwallet.apl.core.http.APIProxy;
import com.apollocurrency.aplwallet.apl.core.peer.Peers;
import com.apollocurrency.aplwallet.apl.crypto.Convert;
import com.apollocurrency.aplwallet.apl.crypto.Crypto;
import com.apollocurrency.aplwallet.apl.util.AplException;
import com.apollocurrency.aplwallet.apl.util.ThreadPool;
import com.apollocurrency.aplwallet.apl.util.env.DirProvider;
import com.apollocurrency.aplwallet.apl.util.env.RuntimeEnvironment;
import com.apollocurrency.aplwallet.apl.util.env.RuntimeMode;
import com.apollocurrency.aplwallet.apl.util.env.ServerStatus;
import org.h2.jdbc.JdbcSQLException;
import org.json.simple.JSONObject;
import org.slf4j.Logger;

import javax.enterprise.inject.spi.CDI;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static com.apollocurrency.aplwallet.apl.core.app.Constants.DEFAULT_PEER_PORT;
import static com.apollocurrency.aplwallet.apl.core.app.Constants.TESTNET_API_SSLPORT;
import static com.apollocurrency.aplwallet.apl.core.app.Constants.TESTNET_PEER_PORT;
import com.apollocurrency.aplwallet.apl.util.AppStatus;
import com.apollocurrency.aplwallet.apl.util.env.RuntimeParams;
import static org.slf4j.LoggerFactory.getLogger;

public final class AplCore {
    private static Logger LOG;// = LoggerFactory.getLogger(AplCore.class);


    private static AplContainer container;
    private static ChainIdService chainIdService;
//    public static final Version VERSION = Version.from("1.24.0");
    public static final Version VERSION = Version.from("1.23.4");

    public static final String APPLICATION = "Apollo";

    private static volatile Time time = new Time.EpochTime();

    private static volatile boolean shutdown = false;

    
//TODO: Core should not be static anymore!  
    public AplCore() {
    }
    
    public static boolean isShutdown() {
        return shutdown;
    }
 
    public static boolean isDesktopApplicationEnabled() {
        return RuntimeEnvironment.isDesktopApplicationEnabled() && aplGlobalObjects.getBooleanProperty("apl.launchDesktopApplication");
    }
    



    public static Blockchain getBlockchain() {
        return BlockchainImpl.getInstance();
    }

    public static BlockchainProcessor getBlockchainProcessor() {
        return BlockchainProcessorImpl.getInstance();
    }

    public static TransactionProcessor getTransactionProcessor() {
        return TransactionProcessorImpl.getInstance();
    }

    public static Transaction.Builder newTransactionBuilder(byte[] senderPublicKey, long amountATM, long feeATM, short deadline, Attachment attachment) {
        return new TransactionImpl.BuilderImpl((byte)1, senderPublicKey, amountATM, feeATM, deadline, (Attachment.AbstractAttachment)attachment);
    }

    public static Transaction.Builder newTransactionBuilder(byte[] transactionBytes) throws AplException.NotValidException {
        return TransactionImpl.newTransactionBuilder(transactionBytes);
    }

    public static Transaction.Builder newTransactionBuilder(JSONObject transactionJSON) throws AplException.NotValidException {
        return TransactionImpl.newTransactionBuilder(transactionJSON);
    }

    public static Transaction.Builder newTransactionBuilder(byte[] transactionBytes, JSONObject prunableAttachments) throws AplException.NotValidException {
        return TransactionImpl.newTransactionBuilder(transactionBytes, prunableAttachments);
    }

    public static int getEpochTime() {
        return time.getTime();
    }

    static void setTime(Time time) {
        AplCore.time = time;
    }


    public void init() {

        System.out.printf("Runtime mode %s\n", AplCoreRuntime.getInstance().getRuntimeMode().getClass().getName());
        // dirProvider = RuntimeEnvironment.getDirProvider();
        LOG = getLogger(AplCore.class);
        LOG.debug("User home folder '{}'", AplCoreRuntime.getInstance().getDirProvider().getUserHomeDir());
        AplGlobalObjects.createPropertiesLoader(AplCoreRuntime.getInstance().getDirProvider());
        if (!VERSION.equals(Version.from(AplGlobalObjects.getPropertiesLoader().getDefaultProperties().getProperty("apl.version")))) {
            LOG.warn("Versions don't match = {} and {}", VERSION, AplGlobalObjects.getPropertiesLoader().getDefaultProperties().getProperty("apl.version"));
            throw new RuntimeException("Using an apl-default.properties file from a version other than " + VERSION + " is not supported!!!");
        }
        startUp();
    }

    public void shutdown() {
        LOG.info("Shutting down...");
        AddOns.shutdown();
        API.shutdown();
        FundingMonitor.shutdown();
        ThreadPool.shutdown();
        BlockchainProcessorImpl.getInstance().shutdown();
        Peers.shutdown();
        Db.shutdown();
        LOG.info(AplCore.APPLICATION + " server " + VERSION + " stopped.");
        container.shutdown();
        AplCore.shutdown = true;
    }
    
    private static void setServerStatus(ServerStatus status, URI wallet) {
        AplCoreRuntime.getInstance().setServerStatus(status, wallet);
    }
    
    private static AplGlobalObjects aplGlobalObjects; // TODO: YL remove static later
    private static volatile boolean initialized = false;

//    private static class Init {
    private void startUp() {

        if (initialized) {
            throw new RuntimeException("Apl.init has already been called");
        }
        initialized = true;

//        static {
            try {
                long startTime = System.currentTimeMillis();
//                AplGlobalObjects.createNtpTime();
                PropertiesLoader propertiesLoader = AplGlobalObjects.getPropertiesLoader();
                AplGlobalObjects.createChainIdService(propertiesLoader.getStringProperty("apl.chainIdFilePath" , "chains.json"));
                AplGlobalObjects.createBlockchainConfig(AplGlobalObjects.getChainIdService().getActiveChain(), propertiesLoader, false);
//                AplGlobalObjects.getChainConfig().init();
//                AplGlobalObjects.getChainConfig().updateToLatestConstants();

                propertiesLoader.loadSystemProperties(
                        Arrays.asList(
                                "socksProxyHost",
                                "socksProxyPort",
                                "apl.enablePeerUPnP"));
                AplCoreRuntime.logSystemProperties();
                Thread secureRandomInitThread = initSecureRandom();
                AppStatus.getInstance().update("Database initialization...");

                checkPorts();
                setServerStatus(ServerStatus.BEFORE_DATABASE, null);
                this.container = AplContainer.builder().containerId("MAIN-APL-CDI").recursiveScanPackages(this.getClass())
                        .annotatedDiscoveryMode().build();
                aplGlobalObjects = CDI.current().select(AplGlobalObjects.class).get();

                Db.init();
//TODO: check: no such file
  //              ChainIdDbMigration.migrate();
                AplGlobalObjects.createBlockDb(new ConnectionProviderImpl());
                migrateDb();

                setServerStatus(ServerStatus.AFTER_DATABASE, null);

                aplGlobalObjects.createNtpTime();
                aplGlobalObjects.getChainConfig().init();
                aplGlobalObjects.getChainConfig().updateToLatestConfig();
                TransactionProcessorImpl.getInstance();
                BlockchainProcessorImpl.getInstance();
                Account.init();
                AccountRestrictions.init();
                AppStatus.getInstance().update("Account ledger initialization...");
                AccountLedger.init();
                Alias.init();
                Asset.init();
                DigitalGoodsStore.init();
                Order.init();
                Poll.init();
                PhasingPoll.init();
                Trade.init();
                AssetTransfer.init();
                AssetDelete.init();
                AssetDividend.init();
                Vote.init();
                PhasingVote.init();
                Currency.init();
                CurrencyBuyOffer.init();
                CurrencySellOffer.init();
                CurrencyFounder.init();
                CurrencyMint.init();
                CurrencyTransfer.init();
                Exchange.init();
                ExchangeRequest.init();
                Shuffling.init();
                ShufflingParticipant.init();
                PrunableMessage.init();
                TaggedData.init();
                AppStatus.getInstance().update("Peer server initialization...");
                Peers.init();
                AppStatus.getInstance().update("API Proxy initialization...");
                APIProxy.init();
                Generator.init();
                AddOns.init();
                AppStatus.getInstance().update("API initialization...");
                API.init();
                DebugTrace.init();
                int timeMultiplier = (aplGlobalObjects.getChainConfig().isTestnet() && Constants.isOffline) ? Math.max(aplGlobalObjects.getIntProperty("apl.timeMultiplier"), 1) : 1;
                ThreadPool.start(timeMultiplier);
                if (timeMultiplier > 1) {
                    setTime(new Time.FasterTime(Math.max(getEpochTime(), AplCore.getBlockchain().getLastBlock().getTimestamp()), timeMultiplier));
                    LOG.info("TIME WILL FLOW " + timeMultiplier + " TIMES FASTER!");
                }
                try {
                    secureRandomInitThread.join(10000);
                }
                catch (InterruptedException ignore) {}
                testSecureRandom();
                long currentTime = System.currentTimeMillis();
                LOG.info("Initialization took " + (currentTime - startTime) / 1000 + " seconds");
                String message = AplCore.APPLICATION + " server " + VERSION + " started successfully.";
                LOG.info(message);
                AppStatus.getInstance().update(message);
                LOG.info("Copyright © 2013-2016 The NXT Core Developers.");
                LOG.info("Copyright © 2016-2017 Jelurida IP B.V..");
                LOG.info("Copyright © 2017-2018 Apollo Foundation.");
                LOG.info("See LICENSE.txt for more information");
                if (API.getWelcomePageUri() != null) {
                    LOG.info("Client UI is at " + API.getWelcomePageUri());
                }
                setServerStatus(ServerStatus.STARTED, API.getWelcomePageUri());

                if (AplGlobalObjects.getChainConfig().isTestnet()) {
                    LOG.info("RUNNING ON TESTNET - DO NOT USE REAL ACCOUNTS!");
                }
            }
            catch (final RuntimeException e) {
                if (e.getMessage() == null || (!e.getMessage().contains(JdbcSQLException.class.getName()) && !e.getMessage().contains(SQLException.class.getName()))) {
                    Throwable exception = e;
                    while (exception.getCause() != null) { //get root cause of RuntimeException
                        exception = exception.getCause();
                    }
                    if (exception.getClass() != JdbcSQLException.class && exception.getClass() != SQLException.class) {
                        throw e; //re-throw non-db exception
                    }
                }
                LOG.error("Database initialization failed ", e);
                //TODO: move DB operations to proper place
                AplCoreRuntime.getInstance().getRuntimeMode().recoverDb();
            }
            catch (Exception e) {
                LOG.error(e.getMessage(), e);
                AppStatus.getInstance().alert(e.getMessage() + "\n" +
                        "See additional information in " + AplCoreRuntime.getInstance().getLogDir() + System.getProperty("file.separator") + "apl.log");
                System.exit(1);
            }
        }

        private static void migrateDb() {
            String secondDbMigrationRequired = Option.get("secondDbMigrationRequired");
            boolean secondMigrationRequired = secondDbMigrationRequired == null || Boolean.parseBoolean(secondDbMigrationRequired);
            if (secondMigrationRequired) {
                Option.set("secondDbMigrationRequired", "true");
                LOG.debug("Db migration required");
                Db.shutdown();
                String dbDir = aplGlobalObjects.getStringProperty(Db.PREFIX + "Dir");
                String targetDbDir = AplCoreRuntime.getInstance().getDbDir(dbDir);
                String dbName = aplGlobalObjects.getStringProperty(Db.PREFIX + "Name");
                String dbUser = aplGlobalObjects.getStringProperty(Db.PREFIX + "Username");
                String dbPassword = aplGlobalObjects.getStringProperty(Db.PREFIX + "Password");
                String legacyDbDir = AplCoreRuntime.getInstance().getDbDir(dbDir, null, false);
                String chainIdDbDir = AplCoreRuntime.getInstance().getDbDir(dbDir, true);
                DbInfoExtractor dbInfoExtractor = new H2DbInfoExtractor(dbName, dbUser, dbPassword);
                DbMigrator dbMigrator = new ChainIdDbMigrator(chainIdDbDir, legacyDbDir, dbInfoExtractor);
                try {
                    AppStatus.getInstance().update("Performing database migration");
                    Path oldDbPath = dbMigrator.migrate(targetDbDir);
                    Db.init();
                    try (Connection connection = Db.getDb().getConnection()) {
                        FullTextTrigger.reindex(connection);
                    }
                    catch (SQLException e) {
                        throw new RuntimeException(e.toString(), e);
                    }
                    AplGlobalObjects.createBlockDb(new ConnectionProviderImpl());
                    Option.set("secondDbMigrationRequired", "false");
                    boolean deleteOldDb = aplGlobalObjects.getBooleanProperty("apl.deleteOldDbAfterMigration");
                    if (deleteOldDb && oldDbPath != null) {
                        Option.set("oldDbPath", oldDbPath.toAbsolutePath().toString());
                    }
                }
                catch (IOException e) {
                    throw new RuntimeException(e.toString(), e);
                }
            }
            performDbMigrationCleanup();
        }

        private static void performDbMigrationCleanup() {
            String dbDir = aplGlobalObjects.getStringProperty(Db.PREFIX + "Dir");
            String targetDbDir = AplCoreRuntime.getInstance().getDbDir(dbDir);
            String oldDbPathOption = Option.get("oldDbPath");
            if (oldDbPathOption != null) {
                Path oldDbPath = Paths.get(oldDbPathOption);
                if (Files.exists(oldDbPath)) {
                    try {
                        ChainIdDbMigrator.deleteAllWithExclusion(oldDbPath, Paths.get(targetDbDir));
                        Option.delete("oldDbPath");
                    }
                    catch (IOException e) {
                        LOG.error("Unable to delete old db");
                    }
                } else {
                    Option.delete("oldDbPath");
                }
            }
        }

        public static void checkPorts() {
            Set<Integer> ports = collectWorkingPorts();
            for (Integer port : ports) {
                if (!RuntimeParams.isTcpPortAvailable(port)) {
                    String portErrorMessage = "Port " + port + " is already in use. Please, shutdown all Apollo processes and restart application!";
                    AppStatus.getInstance().error("ERROR!!! " + portErrorMessage);
                    throw new RuntimeException(portErrorMessage);
                }
            }
        }

        static Set<Integer> collectWorkingPorts() {
            boolean testnet = aplGlobalObjects.getChainConfig().isTestnet();
            final int port = testnet ?  Constants.TESTNET_API_PORT: aplGlobalObjects.getIntProperty("apl.apiServerPort");
            final int sslPort = testnet ? TESTNET_API_SSLPORT : aplGlobalObjects.getIntProperty("apl.apiServerSSLPort");
            boolean enableSSL = aplGlobalObjects.getBooleanProperty("apl.apiSSL");
            int peerPort = -1;

            String myAddress = Convert.emptyToNull(aplGlobalObjects.getStringProperty("apl.myAddress", "").trim());
            if (myAddress != null) {
                try {
                    int portIndex = myAddress.lastIndexOf(":");
                    if (portIndex != -1) {
                        peerPort = Integer.parseInt(myAddress.substring(portIndex + 1));
                    }
                }
                catch (NumberFormatException e) {
                    LOG.error("Unable to parse port in '{}' address",myAddress);
                }
            }
            if (peerPort == -1) {
                peerPort = testnet ? TESTNET_PEER_PORT : DEFAULT_PEER_PORT;
            }
            int peerServerPort = aplGlobalObjects.getIntProperty("apl.peerServerPort");

            Set<Integer> ports = new HashSet<>();
            ports.add(port);
            if (enableSSL) {
                ports.add(sslPort);
            }
            ports.add(peerPort);
            ports.add(testnet ? TESTNET_PEER_PORT : peerServerPort);
            return ports;
        }




    private static Thread initSecureRandom() {
        Thread secureRandomInitThread = new Thread(() -> Crypto.getSecureRandom().nextBytes(new byte[1024]));
        secureRandomInitThread.setDaemon(true);
        secureRandomInitThread.start();
        return secureRandomInitThread;
    }

    private static void testSecureRandom() {
        Thread thread = new Thread(() -> Crypto.getSecureRandom().nextBytes(new byte[1024]));
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join(2000);
            if (thread.isAlive()) {
                throw new RuntimeException("SecureRandom implementation too slow!!! " +
                        "Install haveged if on linux, or set apl.useStrongSecureRandom=false.");
            }
        } catch (InterruptedException ignore) {}
    }

}
