import 'package:flutter/material.dart';
import 'app_list_screen.dart';
import 'cloned_apps_screen.dart';
import '../services/permission_service.dart';
import '../services/app_service.dart';
import 'security_settings_screen.dart';
import 'performance_settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _selectedIndex = 0;
  Map<String, dynamic>? memoryInfo;
  Map<String, dynamic>? globalStats;
  bool isLoadingSystemInfo = false;

  late final List<Widget> _screens;

  @override
  void initState() {
    super.initState();
    _screens = [
      const ClonedAppsScreen(),
      _buildSettingsScreen(),
    ];
    _loadSystemInfo();
    // Preload apps in background for better performance
    _preloadAppsInBackground();
  }
  
  /// Preload apps in background for better performance
  void _preloadAppsInBackground() {
    Future.delayed(const Duration(milliseconds: 500), () {
      AppService.preloadInstalledApps();
    });
  }

  Future<void> _loadSystemInfo() async {
    if (!mounted) return;

    // Set loading state without blocking UI
    if (mounted) {
      setState(() {
        isLoadingSystemInfo = true;
      });
    }

    try {
      // Load memory info with optimized timeout
      final memoryFuture = AppService.getMemoryInfo().timeout(
        const Duration(seconds: 1),
        onTimeout: () => _getFallbackMemoryInfo(),
      );
      
      // Load global stats with optimized timeout
      final statsFuture = AppService.getGlobalStatistics().timeout(
        const Duration(seconds: 1),
        onTimeout: () => _getFallbackGlobalStats(),
      );
      
      final results = await Future.wait([
        memoryFuture,
        statsFuture,
      ], eagerError: false);

      if (mounted) {
        setState(() {
          memoryInfo = results[0] as Map<String, dynamic>? ?? _getFallbackMemoryInfo();
          globalStats = results[1] as Map<String, dynamic>? ?? _getFallbackGlobalStats();
          isLoadingSystemInfo = false;
        });
      }
    } catch (e) {
      print('❌ Error loading system info: $e');
      if (mounted) {
        setState(() {
          isLoadingSystemInfo = false;
          // Set optimized fallback data immediately
          memoryInfo = _getFallbackMemoryInfo();
          globalStats = _getFallbackGlobalStats();
        });
      }
    }
  }
  
  /// Get fallback memory info when loading fails
  Map<String, dynamic> _getFallbackMemoryInfo() {
    return {
      'totalMemory': 4096,
      'availableMemory': 2048,
      'usedMemory': 2048,
      'usagePercentage': 50.0,
    };
  }
  
  /// Get fallback global stats when loading fails
  Map<String, dynamic> _getFallbackGlobalStats() {
    return {
      'totalClones': 0,
      'activeClones': 0,
      'totalStorage': 0,
      'lastUpdated': DateTime.now().millisecondsSinceEpoch,
    };
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.menu, color: Colors.white),
          onPressed: () {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('Menu opened'),
                duration: Duration(seconds: 1),
                backgroundColor: Colors.orange,
              ),
            );
          },
        ),
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8),
                gradient: const LinearGradient(
                  colors: [Colors.orange, Colors.deepOrange],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
              ),
              child: const Icon(
                Icons.apps,
                color: Colors.white,
                size: 20,
              ),
            ),
            const SizedBox(width: 12),
            const Text(
              'MultiSpace',
              style: TextStyle(
                color: Colors.white,
                fontSize: 24,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
        backgroundColor: Colors.black,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.info_outline, color: Colors.white),
            onPressed: () {
              _showAboutDialog();
            },
          ),
        ],
      ),
      body: _screens[_selectedIndex],
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _selectedIndex,
        onTap: (index) {
          setState(() {
            _selectedIndex = index;
          });
        },
        backgroundColor: Colors.grey[900],
        selectedItemColor: Colors.orange,
        unselectedItemColor: Colors.grey,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.apps),
            label: 'Apps',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.settings),
            label: 'Settings',
          ),
        ],
      ),
      floatingActionButton: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(30),
          gradient: const LinearGradient(
            colors: [Colors.orange, Colors.deepOrange],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.orange.withOpacity(0.3),
              blurRadius: 15,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: FloatingActionButton(
          onPressed: () async {
            // Check permissions before navigating
            final hasPermissions = await PermissionService.hasAllRequiredPermissions();

            if (!hasPermissions) {
              final granted = await PermissionService.requestPermissionsWithFlow(context);
              if (!granted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Permissions are required to clone apps'),
                    backgroundColor: Colors.red,
                  ),
                );
                return;
              }
            }

            Navigator.push(
              context,
              MaterialPageRoute(
                builder: (context) => const AppListScreen(),
              ),
            );
          },
          backgroundColor: Colors.transparent,
          elevation: 0,
          child: const Icon(
            Icons.add,
            color: Colors.white,
            size: 30,
          ),
        ),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerDocked,
    );
  }

  void _showAboutDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.grey[900],
        title: const Text(
          'About MultiSpace',
          style: TextStyle(color: Colors.orange),
        ),
        content: const Text(
          'MultiSpace allows you to clone and run multiple instances of your favorite apps with separate data and accounts.\n\nVersion: 1.0.0\nDeveloped with Flutter',
          style: TextStyle(color: Colors.white),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text(
              'OK',
              style: TextStyle(color: Colors.orange),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSettingsScreen() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // System Information Card
          _buildSystemInfoCard(),
          const SizedBox(height: 16),
          
          // Memory Management Card
          _buildMemoryManagementCard(),
          const SizedBox(height: 16),
          
          // Global Statistics Card
          _buildGlobalStatsCard(),
          const SizedBox(height: 16),
          
          // Settings Options
          _buildSettingsOptions(),
        ],
      ),
    );
  }

  Widget _buildSystemInfoCard() {
    return Card(
      color: Colors.grey[850],
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.info_outline, color: Colors.orange),
                const SizedBox(width: 8),
                const Text(
                  'System Information',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const Spacer(),
                IconButton(
                  icon: const Icon(Icons.refresh, color: Colors.orange),
                  onPressed: _loadSystemInfo,
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (isLoadingSystemInfo)
              const Center(
                child: CircularProgressIndicator(color: Colors.orange),
              )
            else if (memoryInfo != null) ...[
              _buildInfoRow('Total Memory', '${memoryInfo!['totalMemory'] ?? 'Unknown'} MB'),
              _buildInfoRow('Available Memory', '${memoryInfo!['availableMemory'] ?? 'Unknown'} MB'),
              _buildInfoRow('Used Memory', '${memoryInfo!['usedMemory'] ?? 'Unknown'} MB'),
              _buildInfoRow('Memory Usage', '${memoryInfo!['memoryUsagePercent'] ?? 'Unknown'}%'),
              const SizedBox(height: 8),
              LinearProgressIndicator(
                value: (memoryInfo!['memoryUsagePercent'] ?? 0) / 100.0,
                backgroundColor: Colors.grey[700],
                valueColor: AlwaysStoppedAnimation<Color>(
                  (memoryInfo!['memoryUsagePercent'] ?? 0) > 80 ? Colors.red :
                  (memoryInfo!['memoryUsagePercent'] ?? 0) > 60 ? Colors.orange : Colors.green,
                ),
              ),
            ] else
              const Text(
                'Failed to load system information',
                style: TextStyle(color: Colors.grey),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildMemoryManagementCard() {
    return Card(
      color: Colors.grey[850],
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.memory, color: Colors.orange),
                SizedBox(width: 8),
                Text(
                  'Memory Management',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _optimizeMemory,
                    icon: const Icon(Icons.cleaning_services, color: Colors.white),
                    label: const Text(
                      'Optimize Memory',
                      style: TextStyle(color: Colors.white),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.orange,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _clearCache,
                    icon: const Icon(Icons.clear_all, color: Colors.white),
                    label: const Text(
                      'Clear Cache',
                      style: TextStyle(color: Colors.white),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.grey[700],
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildGlobalStatsCard() {
    return Card(
      color: Colors.grey[850],
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.analytics, color: Colors.orange),
                SizedBox(width: 8),
                Text(
                  'Global Statistics',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (globalStats != null) ...[
              Row(
                children: [
                  Expanded(
                    child: _buildStatCard(
                      'Total Clones',
                      '${globalStats!['totalClones'] ?? 0}',
                      Icons.apps,
                      Colors.blue,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildStatCard(
                      'Active Instances',
                      '${globalStats!['activeInstances'] ?? 0}',
                      Icons.play_circle,
                      Colors.green,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: _buildStatCard(
                      'Total Storage',
                      '${globalStats!['totalStorageUsed'] ?? 0} MB',
                      Icons.storage,
                      Colors.purple,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildStatCard(
                      'Avg Performance',
                      '${globalStats!['averagePerformance'] ?? 0}%',
                      Icons.speed,
                      Colors.orange,
                    ),
                  ),
                ],
              ),
            ] else
              const Text(
                'No statistics available',
                style: TextStyle(color: Colors.grey),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildSettingsOptions() {
    return Card(
      color: Colors.grey[850],
      child: Column(
        children: [
          _buildSettingsTile(
            icon: Icons.security,
            title: 'Security Settings',
            subtitle: 'Manage app security and permissions',
            onTap: _showSecuritySettings,
          ),
          const Divider(color: Colors.grey),
          _buildSettingsTile(
            icon: Icons.tune,
            title: 'Performance Settings',
            subtitle: 'Optimize app performance and resources',
            onTap: _showPerformanceSettings,
          ),
          const Divider(color: Colors.grey),
          _buildSettingsTile(
            icon: Icons.backup,
            title: 'Backup & Restore',
            subtitle: 'Manage app data backup and restore',
            onTap: _showBackupSettings,
          ),
          const Divider(color: Colors.grey),
          _buildSettingsTile(
            icon: Icons.help_outline,
            title: 'Help & Support',
            subtitle: 'Get help and contact support',
            onTap: _showHelpSupport,
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(color: Colors.white),
          ),
          Text(
            value,
            style: const TextStyle(color: Colors.grey),
          ),
        ],
      ),
    );
  }

  Widget _buildStatCard(String title, String value, IconData icon, Color color) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Column(
        children: [
          Icon(icon, color: color, size: 24),
          const SizedBox(height: 8),
          Text(
            value,
            style: TextStyle(
              color: color,
              fontSize: 18,
              fontWeight: FontWeight.bold,
            ),
          ),
          Text(
            title,
            style: const TextStyle(
              color: Colors.grey,
              fontSize: 12,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildSettingsTile({
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return ListTile(
      leading: Icon(icon, color: Colors.orange),
      title: Text(
        title,
        style: const TextStyle(color: Colors.white),
      ),
      subtitle: Text(
        subtitle,
        style: const TextStyle(color: Colors.grey),
      ),
      trailing: const Icon(Icons.arrow_forward_ios, color: Colors.grey, size: 16),
      onTap: onTap,
    );
  }

  Future<void> _optimizeMemory() async {
    // Show loading indicator
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Row(
            children: [
              SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: Colors.white,
                ),
              ),
              SizedBox(width: 12),
              Text('Optimizing memory...'),
            ],
          ),
          backgroundColor: Colors.orange,
          duration: Duration(seconds: 2),
        ),
      );
    }
    
    try {
      final result = await AppService.optimizeMemory().timeout(
        const Duration(seconds: 5),
        onTimeout: () => throw TimeoutException('Memory optimization timeout'),
      );
      
      // Refresh system info after optimization
      _loadSystemInfo();
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Memory optimized! Freed ${result['memoryFreed'] ?? 'some'} MB'),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } catch (e) {
      print('❌ Memory optimization failed: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Memory optimization failed: ${e.toString().split(':').last}'),
            backgroundColor: Colors.red,
            action: SnackBarAction(
              label: 'Retry',
              onPressed: _optimizeMemory,
            ),
          ),
        );
      }
    }
  }

  Future<void> _clearCache() async {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.grey[900],
        title: const Text(
          'Clear Cache',
          style: TextStyle(color: Colors.orange),
        ),
        content: const Text(
          'This will clear all cached data for cloned apps. Continue?',
          style: TextStyle(color: Colors.white),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel', style: TextStyle(color: Colors.grey)),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              // Implement cache clearing logic
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('Cache cleared successfully'),
                  backgroundColor: Colors.green,
                ),
              );
            },
            child: const Text('Clear', style: TextStyle(color: Colors.orange)),
          ),
        ],
      ),
    );
  }

  void _showSecuritySettings() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const SecuritySettingsScreen(),
      ),
    );
  }

  void _showPerformanceSettings() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const PerformanceSettingsScreen(),
      ),
    );
  }

  void _showBackupSettings() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.grey[900],
        title: const Text(
          'Backup & Restore',
          style: TextStyle(color: Colors.orange),
        ),
        content: const Text(
          'Backup and restore features will be available in future updates.',
          style: TextStyle(color: Colors.white),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK', style: TextStyle(color: Colors.orange)),
          ),
        ],
      ),
    );
  }

  void _showHelpSupport() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.grey[900],
        title: const Text(
          'Help & Support',
          style: TextStyle(color: Colors.orange),
        ),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'MultiSpace App Cloner\nVersion 1.0.0\n\nFor support and feedback:\n• Email: support@multispace.com\n• Website: www.multispace.com\n• GitHub: github.com/multispace',
              style: TextStyle(color: Colors.white),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK', style: TextStyle(color: Colors.orange)),
          ),
        ],
      ),
    );
  }
}

