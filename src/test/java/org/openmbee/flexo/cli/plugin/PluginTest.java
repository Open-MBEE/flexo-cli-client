package org.openmbee.flexo.cli.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.config.FlexoConfig;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PluginLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testGetPluginDirectory() {
        String pluginDir = PluginLoader.getPluginDirectory();
        assertNotNull(pluginDir);
        assertTrue(pluginDir.contains(".flexo"));
        assertTrue(pluginDir.contains("plugins"));
    }

    @Test
    void testLoadPlugins_NoPluginDirectory() {
        FlexoCLI cli = new FlexoCLI();
        FlexoConfig config = new FlexoConfig(false);
        PluginContext context = new PluginContext(config, cli);

        System.setProperty("user.home", tempDir.toString());
        
        List<FlexoPlugin> plugins = PluginLoader.loadPlugins(context);
        assertNotNull(plugins);
        assertTrue(plugins.isEmpty());
    }

    @Test
    void testLoadPlugins_EmptyPluginDirectory() throws Exception {
        File pluginsDir = tempDir.resolve(".flexo/plugins").toFile();
        pluginsDir.mkdirs();

        FlexoCLI cli = new FlexoCLI();
        FlexoConfig config = new FlexoConfig(false);
        PluginContext context = new PluginContext(config, cli);

        System.setProperty("user.home", tempDir.toString());
        
        List<FlexoPlugin> plugins = PluginLoader.loadPlugins(context);
        assertNotNull(plugins);
        assertTrue(plugins.isEmpty());
    }

    @Test
    void testLoadPlugins_NoJarFiles() throws Exception {
        File pluginsDir = tempDir.resolve(".flexo/plugins").toFile();
        pluginsDir.mkdirs();
        
        File txtFile = new File(pluginsDir, "readme.txt");
        txtFile.createNewFile();

        FlexoCLI cli = new FlexoCLI();
        FlexoConfig config = new FlexoConfig(false);
        PluginContext context = new PluginContext(config, cli);

        System.setProperty("user.home", tempDir.toString());
        
        List<FlexoPlugin> plugins = PluginLoader.loadPlugins(context);
        assertNotNull(plugins);
        assertTrue(plugins.isEmpty());
    }
}

class PluginContextTest {

    @Test
    void testConstructor() {
        FlexoCLI cli = new FlexoCLI();
        FlexoConfig config = new FlexoConfig(false);
        
        PluginContext context = new PluginContext(config, cli);
        
        assertNotNull(context);
        assertEquals(config, context.getConfig());
    }

    @Test
    void testGetOrgId_FromParent() {
        FlexoCLI cli = new FlexoCLI() {
            @Override
            public String getOrgId() {
                return "test-org";
            }
        };
        FlexoConfig config = new FlexoConfig(false);
        
        PluginContext context = new PluginContext(config, cli);
        
        assertEquals("test-org", context.getOrgId());
    }

    @Test
    void testGetOrgId_FromConfig() {
        FlexoCLI cli = new FlexoCLI() {
            @Override
            public String getOrgId() {
                return null;
            }
        };
        FlexoConfig config = new FlexoConfig(false);
        config.set("default.org", "config-org");
        
        PluginContext context = new PluginContext(config, cli);
        
        assertEquals("config-org", context.getOrgId());
    }

    @Test
    void testGetRepoId_FromParent() {
        FlexoCLI cli = new FlexoCLI() {
            @Override
            public String getRepoId() {
                return "test-repo";
            }
        };
        FlexoConfig config = new FlexoConfig(false);
        
        PluginContext context = new PluginContext(config, cli);
        
        assertEquals("test-repo", context.getRepoId());
    }

    @Test
    void testGetRepoId_FromConfig() {
        FlexoCLI cli = new FlexoCLI() {
            @Override
            public String getRepoId() {
                return null;
            }
        };
        FlexoConfig config = new FlexoConfig(false);
        config.set("default.repo", "config-repo");
        
        PluginContext context = new PluginContext(config, cli);
        
        assertEquals("config-repo", context.getRepoId());
    }

    @Test
    void testIsVerbose() {
        FlexoCLI cli = new FlexoCLI() {
            @Override
            public boolean isVerbose() {
                return true;
            }
        };
        FlexoConfig config = new FlexoConfig(false);
        
        PluginContext context = new PluginContext(config, cli);
        
        assertTrue(context.isVerbose());
    }

    @Test
    void testIsNoColor() {
        FlexoCLI cli = new FlexoCLI() {
            @Override
            public boolean isNoColor() {
                return true;
            }
        };
        FlexoConfig config = new FlexoConfig(false);
        
        PluginContext context = new PluginContext(config, cli);
        
        assertTrue(context.isNoColor());
    }

    @Test
    void testGetMmsUrl() {
        FlexoCLI cli = new FlexoCLI();
        FlexoConfig config = new FlexoConfig(false);
        
        PluginContext context = new PluginContext(config, cli);
        
        assertEquals("http://localhost:8080", context.getMmsUrl());
    }

    @Test
    void testCreateClient() {
        FlexoCLI cli = new FlexoCLI();
        FlexoConfig config = new FlexoConfig(false);
        
        PluginContext context = new PluginContext(config, cli);
        
        assertNotNull(context.createClient());
    }
}

class PluginCommandTest {

    @Test
    void testSetContext() {
        FlexoCLI cli = new FlexoCLI();
        FlexoConfig config = new FlexoConfig(false);
        PluginContext pluginContext = new PluginContext(config, cli);

        TestPluginCommand command = new TestPluginCommand();
        command.setContext(pluginContext);

        assertEquals(pluginContext, command.getTestContext());
    }

    @Test
    void testGetClient() {
        FlexoCLI cli = new FlexoCLI();
        FlexoConfig config = new FlexoConfig(false);
        PluginContext pluginContext = new PluginContext(config, cli);

        TestPluginCommand command = new TestPluginCommand();
        command.setContext(pluginContext);

        assertNotNull(command.getClient());
    }

    @Test
    void testGetConfig() {
        FlexoCLI cli = new FlexoCLI();
        FlexoConfig config = new FlexoConfig(false);
        PluginContext pluginContext = new PluginContext(config, cli);

        TestPluginCommand command = new TestPluginCommand();
        command.setContext(pluginContext);

        assertEquals(config, command.getConfig());
    }

    @Test
    void testGetOrgId() {
        FlexoCLI cli = new FlexoCLI();
        FlexoConfig config = new FlexoConfig(false);
        config.set("default.org", "test-org");
        PluginContext pluginContext = new PluginContext(config, cli);

        TestPluginCommand command = new TestPluginCommand();
        command.setContext(pluginContext);

        assertEquals("test-org", command.getOrgId());
    }

    @Test
    void testGetRepoId() {
        FlexoCLI cli = new FlexoCLI();
        FlexoConfig config = new FlexoConfig(false);
        config.set("default.repo", "test-repo");
        PluginContext pluginContext = new PluginContext(config, cli);

        TestPluginCommand command = new TestPluginCommand();
        command.setContext(pluginContext);

        assertEquals("test-repo", command.getRepoId());
    }

    @Test
    void testIsVerbose() {
        FlexoCLI cli = new FlexoCLI() {
            @Override
            public boolean isVerbose() {
                return true;
            }
        };
        FlexoConfig config = new FlexoConfig(false);
        PluginContext pluginContext = new PluginContext(config, cli);

        TestPluginCommand command = new TestPluginCommand();
        command.setContext(pluginContext);

        assertTrue(command.isVerbose());
    }

    static class TestPluginCommand extends PluginCommand {
        public PluginContext getTestContext() {
            return context;
        }

        @Override
        public void run() {
        }
    }
}
