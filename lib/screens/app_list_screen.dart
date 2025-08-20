import 'dart:async';
import 'package:flutter/material.dart';
import '../services/app_service.dart';
import '../models/app_info.dart';

class AppListScreen extends StatefulWidget {
  const AppListScreen({super.key});

  @override
  State<AppListScreen> createState() => _AppListScreenState();
}

class _AppListScreenState extends State<AppListScreen> {
  List<AppInfo> installedApps = [];
  List<Map<String, dynamic>> selectedApps = [];
  bool isLoading = true;
  bool isRefreshing = false;
  String searchQuery = '';

  // Optimized pagination
  static const int _pageSize = 30;
  static const int _preloadSize = 50;
  int _currentPage = 0;
  bool _hasMoreApps = true;
  bool _isLoadingMore = false;
  bool _isPreloading = false;
  Timer? _searchDebouncer;
  Timer? _preloadTimer;

  @override
  void initState() {
    super.initState();
    _loadInstalledApps();
  }

  @override
  void dispose() {
    _searchDebouncer?.cancel();
    _preloadTimer?.cancel();
    super.dispose();
  }

  /// Debounced search to improve performance
  void _onSearchChanged(String query) {
    _searchDebouncer?.cancel();
    _searchDebouncer = Timer(const Duration(milliseconds: 300), () {
      if (mounted) {
        setState(() {
          searchQuery = query;
        });
      }
    });
  }

  Future<void> _loadInstalledApps({bool forceRefresh = false}) async {
    if (!mounted) return;

    setState(() {
      if (forceRefresh) {
        isRefreshing = true;
      } else {
        isLoading = true;
      }
    });

    try {
      // Load ALL installed apps (not just first batch)
      final apps = await AppService.getInstalledApps(
        forceRefresh: forceRefresh,
        useCache: !forceRefresh,
        maxResults: null, // Load all apps, no limit
        includeIcons: true,
        optimizeForSpeed: false, // Don't optimize for speed to get all apps
        excludeSystemApps: true, // Exclude system apps for better UX
      ).timeout(
        const Duration(seconds: 10), // Longer timeout for all apps
        onTimeout: () {
          print('⚠️ App loading timeout, using cached data');
          return AppService.getCachedApps() ?? [];
        },
      );

      if (mounted) {
        setState(() {
          installedApps = apps;
          _currentPage = 0;
          _hasMoreApps = false; // All apps loaded at once
          isLoading = false;
          isRefreshing = false;
        });

        print('✅ Loaded ${apps.length} apps successfully (ALL APPS)');

        // No need to preload since all apps are already loaded
      }
    } catch (e) {
      print('❌ Error loading apps: $e');
      if (mounted) {
        setState(() {
          // Use cached apps as fallback (all cached apps)
          installedApps = AppService.getCachedApps() ?? [];
          isLoading = false;
          isRefreshing = false;
        });

        String errorMessage = 'Failed to load apps';
        if (e.toString().contains('PERMISSION_ERROR')) {
          errorMessage = 'Permission required to access installed apps. Please grant QUERY_ALL_PACKAGES permission.';
        } else if (e.toString().contains('timeout')) {
          errorMessage = 'App loading timed out. Please try again.';
        }

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(errorMessage),
            backgroundColor: Colors.red,
            duration: Duration(seconds: 5),
            action: SnackBarAction(
              label: 'Retry',
              textColor: Colors.white,
              onPressed: () => _loadInstalledApps(forceRefresh: true),
            ),
          ),
        );
      }
    }
  }

  /// Start preloading apps in background
  void _startPreloading() {
    _preloadTimer?.cancel();
    _preloadTimer = Timer(const Duration(milliseconds: 500), () {
      _preloadMoreApps();
    });
  }

  /// Preload more apps in background
  Future<void> _preloadMoreApps() async {
    if (_isPreloading || !_hasMoreApps) return;

    setState(() {
      _isPreloading = true;
    });

    try {
      final moreApps = await AppService.loadMoreApps(
        offset: installedApps.length,
        limit: _preloadSize,
        includeIcons: false, // Load icons later for better performance
      );

      if (mounted && moreApps.isNotEmpty) {
        setState(() {
          installedApps.addAll(moreApps);
          _hasMoreApps = moreApps.length >= _preloadSize;
          _isPreloading = false;
        });

        print('🔄 Preloaded ${moreApps.length} more apps');
      } else {
        setState(() {
          _hasMoreApps = false;
          _isPreloading = false;
        });
      }
    } catch (e) {
      print('❌ Error preloading apps: $e');
      setState(() {
        _isPreloading = false;
      });
    }
  }

  /// Load more apps when user scrolls
  Future<void> _loadMoreApps() async {
    if (_isLoadingMore || !_hasMoreApps) return;

    setState(() {
      _isLoadingMore = true;
    });

    try {
      final moreApps = await AppService.loadMoreApps(
        offset: installedApps.length,
        limit: _pageSize,
        includeIcons: true,
      );

      if (mounted) {
        setState(() {
          installedApps.addAll(moreApps);
          _currentPage++;
          _hasMoreApps = moreApps.length >= _pageSize;
          _isLoadingMore = false;
        });

        print('📄 Loaded ${moreApps.length} more apps (page ${_currentPage})');
      }
    } catch (e) {
      print('❌ Error loading more apps: $e');
      setState(() {
        _isLoadingMore = false;
      });
    }
  }



  List<AppInfo> get filteredApps {
    if (searchQuery.isEmpty) {
      return installedApps;
    }
    return installedApps
        .where((app) =>
            app.appName.toLowerCase().contains(searchQuery.toLowerCase()))
        .toList();
  }

  void _toggleAppSelection(String packageName) {
    setState(() {
      final existingIndex = selectedApps.indexWhere(
          (app) => app['packageName'] == packageName);
      
      if (existingIndex != -1) {
        selectedApps.removeAt(existingIndex);
      } else {
        final appInfo = installedApps.firstWhere(
          (app) => app.packageName == packageName,
          orElse: () => AppInfo(
            packageName: packageName,
            appName: 'Unknown App',
            icon: null,
            color: Colors.grey,
          ),
        );
        
        selectedApps.add({
          'packageName': packageName,
          'appName': appInfo.appName,
          'customName': null,
        });
      }
    });
  }

  Future<void> _cloneSelectedApps() async {
    if (selectedApps.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please select at least one app to clone'),
          backgroundColor: Colors.orange,
        ),
      );
      return;
    }

    // Show custom name dialog for single app cloning
    String? customName;
    if (selectedApps.length == 1) {
      final controller = TextEditingController();
      final shouldCustomize = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          backgroundColor: Colors.grey[900],
          title: const Text('Customize Clone',
              style: TextStyle(color: Colors.orange)),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Enter a custom name for the cloned app (optional):',
                style: TextStyle(color: Colors.white),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: controller,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(
                  hintText: 'e.g., Bitcoin Wallet Personal',
                  hintStyle: TextStyle(color: Colors.grey),
                  enabledBorder: UnderlineInputBorder(
                    borderSide: BorderSide(color: Colors.orange),
                  ),
                  focusedBorder: UnderlineInputBorder(
                    borderSide: BorderSide(color: Colors.orange),
                  ),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Skip', style: TextStyle(color: Colors.grey)),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context, true),
              child:
                  const Text('Clone', style: TextStyle(color: Colors.orange)),
            ),
          ],
        ),
      );

      if (shouldCustomize == true && controller.text.trim().isNotEmpty) {
        customName = controller.text.trim();
      }
    }

    // Show optimized cloning dialog
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.grey[900],
        title:
            const Text('Fast Cloning', style: TextStyle(color: Colors.orange)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(color: Colors.orange),
            const SizedBox(height: 16),
            Text(
              'Cloning ${selectedApps.length} app(s)...\nOptimized for speed!',
              style: const TextStyle(color: Colors.white),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );

    try {
      int successCount = 0;
      int failCount = 0;

      // Optimized parallel cloning for better performance
      final futures = selectedApps.map((appData) async {
        try {
          final packageName = appData['packageName'] as String;
          final success =
              await AppService.cloneApp(packageName, customName: customName);
          if (success) {
            successCount++;
          } else {
            failCount++;
          }
        } catch (e) {
          failCount++;
          print('Error cloning ${appData['packageName']}: $e');
        }
      });

      await Future.wait(futures);

      // Small delay for UI feedback
      await Future.delayed(const Duration(milliseconds: 500));

      if (mounted) {
        Navigator.of(context).pop(); // Close loading dialog

        showDialog(
          context: context,
          builder: (context) => AlertDialog(
            backgroundColor: Colors.grey[900],
            title: Text(
              successCount > 0 ? 'Cloning Complete!' : 'Cloning Failed',
              style: TextStyle(
                  color: successCount > 0 ? Colors.green : Colors.red),
            ),
            content: Text(
              successCount > 0
                  ? '$successCount app(s) successfully cloned${failCount > 0 ? ', $failCount failed' : ''}!\n\nYou can now use multiple instances with complete data isolation.'
                  : 'Failed to clone apps. Please try again.',
              style: const TextStyle(color: Colors.white),
            ),
            actions: [
              TextButton(
                onPressed: () {
                  Navigator.of(context).pop();
                  Navigator.of(context).pop(); // Go back to home screen
                },
                child: const Text('OK', style: TextStyle(color: Colors.orange)),
              ),
            ],
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        Navigator.of(context).pop(); // Close loading dialog
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error cloning apps: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.orange),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text(
          'Add to MultiSpace',
          style: TextStyle(
            color: Colors.orange,
            fontSize: 20,
            fontWeight: FontWeight.w500,
          ),
        ),
        backgroundColor: Colors.black,
        elevation: 0,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(120),
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(16),
                child: TextField(
                  onChanged: _onSearchChanged,
                  style: const TextStyle(color: Colors.white),
                  decoration: InputDecoration(
                    hintText: 'Search apps...',
                    hintStyle: const TextStyle(color: Colors.grey),
                    prefixIcon: const Icon(Icons.search, color: Colors.orange),
                    filled: true,
                    fillColor: Colors.grey[800],
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
              ),
              // App count display
              Container(
                height: 40,
                child: Container(
                  decoration: BoxDecoration(
                    color: Colors.orange,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Center(
                    child: Text(
                      'All Apps (${installedApps.length})',
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
      body: Column(
        children: [
          if (selectedApps.isNotEmpty)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              color: Colors.grey[800],
              child: Text(
                '${selectedApps.length} app(s) selected',
                style: const TextStyle(
                  color: Colors.orange,
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
                textAlign: TextAlign.center,
              ),
            ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () => _loadInstalledApps(forceRefresh: true),
              child: GridView.builder(
                padding: const EdgeInsets.all(16),
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 4,
                  childAspectRatio: 0.8,
                  crossAxisSpacing: 12,
                  mainAxisSpacing: 12,
                ),
                itemCount: filteredApps.length,
                itemBuilder: (context, index) {
                  final app = filteredApps[index];
                  final isSelected = selectedApps.any((selected) => 
                      selected['packageName'] == app.packageName);
                  
                  return _buildAppItem(app, isSelected);
                },
              ),
            ),
          ),
          if (selectedApps.isNotEmpty)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: const BoxDecoration(
                color: Color(0xFF212121),
                boxShadow: [
                  BoxShadow(
                    color: Color(0x4D000000),
                    blurRadius: 8,
                    offset: Offset(0, -4),
                  ),
                ],
              ),
              child: ElevatedButton(
                onPressed: _cloneSelectedApps,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.orange,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: Text(
                  'CLONE ${selectedApps.length} APP${selectedApps.length > 1 ? 'S' : ''}',
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildAppItem(AppInfo app, bool isSelected) {
    return GestureDetector(
      onTap: () => _toggleAppSelection(app.packageName),
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          border: isSelected
              ? Border.all(color: Colors.orange, width: 2)
              : Border.all(color: Colors.transparent, width: 2),
          color: isSelected
              ? Colors.orange.withOpacity(0.1)
              : Colors.transparent,
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Stack(
              children: [
                Container(
                  width: 60,
                  height: 60,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(12),
                    color: app.color,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.3),
                        blurRadius: 8,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: app.icon != null
                      ? ClipRRect(
                          borderRadius: BorderRadius.circular(12),
                          child: Image.memory(
                            app.icon!,
                            width: 60,
                            height: 60,
                            fit: BoxFit.cover,
                          ),
                        )
                      : const Icon(
                          Icons.android,
                          color: Colors.white,
                          size: 30,
                        ),
                ),
                if (isSelected)
                  Positioned(
                    top: -2,
                    right: -2,
                    child: Container(
                      width: 20,
                      height: 20,
                      decoration: const BoxDecoration(
                        color: Colors.orange,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.check,
                        color: Colors.white,
                        size: 14,
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              app.appName,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
              textAlign: TextAlign.center,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }







  Widget _buildDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(
                color: Colors.white, fontWeight: FontWeight.bold),
          ),
          Text(
            value,
            style: const TextStyle(color: Colors.grey),
          ),
        ],
      ),
    );
  }
}
