/*
 *  Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.shard;

import com.apollocurrency.aplwallet.apl.core.app.observer.events.Async;
import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.db.DatabaseManager;
import com.apollocurrency.aplwallet.apl.core.db.DerivedTablesRegistry;
import com.apollocurrency.aplwallet.apl.core.db.dao.ShardDao;
import com.apollocurrency.aplwallet.apl.core.db.dao.model.Shard;
import com.apollocurrency.aplwallet.apl.core.db.derived.PrunableDbTable;
import com.apollocurrency.aplwallet.apl.core.shard.helper.CsvExporterImpl;
import com.apollocurrency.aplwallet.apl.core.shard.observer.TrimData;
import com.apollocurrency.aplwallet.apl.util.FileUtils;
import com.apollocurrency.aplwallet.apl.util.Zip;
import com.apollocurrency.aplwallet.apl.util.env.dirprovider.DirProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import javax.enterprise.event.Observes;
@Slf4j
public class ShardPrunableZipHashCalculator {
    private DerivedTablesRegistry registry;
    private Zip zip;
    private DatabaseManager databaseManager;
    private ShardDao shardDao;
    private BlockchainConfig blockchainConfig;
    private DirProvider dirProvider;
    private int lastPruningTime = 0;

    public void onTrimDone(@Observes @Async TrimData trimData) {
        tryRecalculatePrunableArchiveHashes(trimData.getPruningTime());
    }

    public void tryRecalculatePrunableArchiveHashes(int time) {
        if (time > lastPruningTime) {
            log.debug("Recalculate prunable archive hashes at {}", time);
            lastPruningTime = time;
            recalculatePrunableArchiveHashes();
        }
    }

    private void recalculatePrunableArchiveHashes() {
        List<Shard> allCompletedShards = shardDao.getAllCompletedShards().stream().filter(shard -> shard.getPrunableZipHash() != null).collect(Collectors.toList()); // TODO change to completed and imported
        allCompletedShards.forEach(shard -> {
            try {
                Path tempDirectory = Files.createTempDirectory("shard-" + shard.getShardId());
                CsvExporterImpl csvExporter = new CsvExporterImpl(databaseManager, tempDirectory);
                List<PrunableDbTable> prunableTables = registry.getDerivedTables().stream().filter(t -> t instanceof PrunableDbTable).map(t -> (PrunableDbTable) t).collect(Collectors.toList());
                prunableTables.forEach(t -> csvExporter.exportPrunableDerivedTable(t, shard.getShardHeight(), lastPruningTime, 100));
                long count = Files.list(tempDirectory).count();
                ShardNameHelper shardNameHelper = new ShardNameHelper();
                String prunableArchiveName = shardNameHelper.getPrunableShardArchiveNameByShardId(shard.getShardId(), blockchainConfig.getChain().getChainId());
                Path prunableArchivePath = dirProvider.getDataExportDir().resolve(prunableArchiveName);
                if (count == 0) {
                    shard.setPrunableZipHash(null);
                    shardDao.updateShard(shard);
                    Files.deleteIfExists(prunableArchivePath);
                } else {
                    String zipName = "shard-" + shard.getShardId() + ".zip";
                    byte[] hash = zip.compressAndHash(zipName, tempDirectory.toAbsolutePath().toString(), 0L, null, false);
                    Files.move(tempDirectory.resolve(zipName), prunableArchivePath, StandardCopyOption.REPLACE_EXISTING);
                    shard.setPrunableZipHash(hash);
                    shardDao.updateShard(shard);
                }
                FileUtils.clearDirectorySilently(tempDirectory);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
