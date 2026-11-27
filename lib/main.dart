import 'package:flutter/material.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const TataApp());
}

class TataApp extends StatelessWidget {
  const TataApp({super.key});

  @override
  Widget build(BuildContext context) {
    const green = Color(0xFF16865B);
    return MaterialApp(
      title: 'Tata AI',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: green,
          brightness: Brightness.light,
          surface: const Color(0xFFF8FAF9),
        ),
        scaffoldBackgroundColor: const Color(0xFFF8FAF9),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFFF8FAF9),
          surfaceTintColor: Colors.transparent,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(24),
            borderSide: const BorderSide(color: Color(0xFFE4EAE6)),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(24),
            borderSide: const BorderSide(color: Color(0xFFE4EAE6)),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(24),
            borderSide: const BorderSide(color: green, width: 1.4),
          ),
        ),
      ),
      home: const TataHomePage(),
    );
  }
}

class TataHomePage extends StatefulWidget {
  const TataHomePage({super.key});

  @override
  State<TataHomePage> createState() => _TataHomePageState();
}

class _TataHomePageState extends State<TataHomePage> {
  final _controller = TextEditingController();
  final List<String> _messages = <String>[];
  String _mode = 'Otomatis';

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _sendMessage() {
    final message = _controller.text.trim();
    if (message.isEmpty) return;
    setState(() {
      _messages.add(message);
      _controller.clear();
    });
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Antarmuka awal siap. Mesin AI akan ditambahkan pada tahap berikutnya.'),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 20,
        title: const Row(
          children: [
            CircleAvatar(
              radius: 17,
              backgroundColor: Color(0xFF16865B),
              child: Icon(Icons.auto_awesome, color: Colors.white, size: 19),
            ),
            SizedBox(width: 10),
            Text('Tata AI', style: TextStyle(fontWeight: FontWeight.w700)),
          ],
        ),
        actions: [
          PopupMenuButton<String>(
            tooltip: 'Pilih mode AI',
            initialValue: _mode,
            onSelected: (value) => setState(() => _mode = value),
            itemBuilder: (context) => const [
              PopupMenuItem(value: 'Otomatis', child: Text('Otomatis')),
              PopupMenuItem(value: 'Offline', child: Text('Offline')),
              PopupMenuItem(value: 'Online', child: Text('Online')),
            ],
            child: Container(
              margin: const EdgeInsets.only(right: 16),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: const Color(0xFFE8F4ED),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Row(
                children: [
                  const Icon(Icons.sync, size: 16, color: Color(0xFF16865B)),
                  const SizedBox(width: 5),
                  Text(_mode, style: const TextStyle(
                    color: Color(0xFF126B49),
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  )),
                  const SizedBox(width: 3),
                  const Icon(Icons.keyboard_arrow_down, size: 16, color: Color(0xFF126B49)),
                ],
              ),
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: _messages.isEmpty
                  ? Center(
                      child: SingleChildScrollView(
                        padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 24),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Container(
                              width: 76,
                              height: 76,
                              decoration: BoxDecoration(
                                color: const Color(0xFFE5F3EA),
                                borderRadius: BorderRadius.circular(25),
                              ),
                              child: const Icon(Icons.auto_awesome, size: 38, color: Color(0xFF16865B)),
                            ),
                            const SizedBox(height: 22),
                            const Text(
                              'Halo, saya Tata',
                              style: TextStyle(fontSize: 25, fontWeight: FontWeight.w700),
                              textAlign: TextAlign.center,
                            ),
                            const SizedBox(height: 9),
                            const Text(
                              'Asisten AI yang dirancang ringan dan dapat berkembang untuk digunakan secara offline maupun online.',
                              textAlign: TextAlign.center,
                              style: TextStyle(color: Color(0xFF68736D), height: 1.5),
                            ),
                            const SizedBox(height: 24),
                            const _CapabilityTile(
                              icon: Icons.wifi_off_rounded,
                              title: 'Siap untuk offline',
                              subtitle: 'Riwayat dan fitur lokal dirancang tersimpan di perangkat.',
                            ),
                            const SizedBox(height: 10),
                            const _CapabilityTile(
                              icon: Icons.cloud_outlined,
                              title: 'Terhubung saat online',
                              subtitle: 'Mesin AI online akan ditambahkan pada tahap integrasi.',
                            ),
                          ],
                        ),
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.fromLTRB(18, 18, 18, 24),
                      itemCount: _messages.length,
                      itemBuilder: (context, index) => Align(
                        alignment: Alignment.centerRight,
                        child: Container(
                          constraints: const BoxConstraints(maxWidth: 300),
                          margin: const EdgeInsets.only(bottom: 12),
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                          decoration: BoxDecoration(
                            color: const Color(0xFF16865B),
                            borderRadius: BorderRadius.circular(18),
                          ),
                          child: Text(_messages[index], style: const TextStyle(color: Colors.white)),
                        ),
                      ),
                    ),
            ),
            Container(
              padding: const EdgeInsets.fromLTRB(14, 10, 14, 14),
              decoration: const BoxDecoration(
                color: Colors.white,
                border: Border(top: BorderSide(color: Color(0xFFE9EEEB))),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: TextField(
                      controller: _controller,
                      minLines: 1,
                      maxLines: 5,
                      textInputAction: TextInputAction.send,
                      onSubmitted: (_) => _sendMessage(),
                      decoration: const InputDecoration(
                        hintText: 'Tulis pesan untuk Tata...',
                        contentPadding: EdgeInsets.symmetric(horizontal: 18, vertical: 13),
                      ),
                    ),
                  ),
                  const SizedBox(width: 9),
                  Material(
                    color: const Color(0xFF16865B),
                    borderRadius: BorderRadius.circular(24),
                    child: InkWell(
                      onTap: _sendMessage,
                      borderRadius: BorderRadius.circular(24),
                      child: const SizedBox(
                        width: 48,
                        height: 48,
                        child: Icon(Icons.arrow_upward_rounded, color: Colors.white),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CapabilityTile extends StatelessWidget {
  const _CapabilityTile({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE8EEEA)),
      ),
      child: Row(
        children: [
          Icon(icon, color: const Color(0xFF16865B), size: 23),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
                const SizedBox(height: 3),
                Text(subtitle, style: const TextStyle(color: Color(0xFF68736D), fontSize: 12)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
