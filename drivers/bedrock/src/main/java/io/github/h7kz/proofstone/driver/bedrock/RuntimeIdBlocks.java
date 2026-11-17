package io.github.h7kz.proofstone.driver.bedrock;

import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

/**
 * Block definitions for the packets that name a block by its runtime id (the creative content's block items). The driver
 * reads items by identifier and count and never a block's state, so a definition only has to carry the id the server sent:
 * nothing is looked up or predicted. Found by the first real join (T-M9-06): the library's decoder dereferenced a null
 * registry.
 */
final class RuntimeIdBlocks implements DefinitionRegistry<BlockDefinition> {

    @Override
    public BlockDefinition getDefinition(int runtimeId) {
        return () -> runtimeId;
    }

    @Override
    public boolean isRegistered(BlockDefinition definition) {
        return definition != null;
    }
}
