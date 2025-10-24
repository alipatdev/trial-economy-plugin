# Trial Economy

Sistema economy ad alte prestazioni per server Minecraft Spigot 1.21.8, progettato per gestire 500+ giocatori concorrenti con zero impatto TPS.

## 🚀 Caratteristiche Principali

- **Database H2 Embedded** con connection pooling HikariCP
- **Operazioni 100% Asincrone** - nessun blocco del main thread
- **Sistema di Caching Avanzato** - bilanciamenti in memoria per accesso istantaneo
- **Supporto Giocatori Offline** - trasferisci denaro anche a chi è offline
- **Precisione Assoluta** - uso di `BigDecimal` per evitare errori di arrotondamento
- **Transaction Logging** - audit completo di tutte le transazioni
- **Thread-Safe** - operazioni concorrenti sicure con `ConcurrentHashMap`
- **Auto-Save Intelligente** - salvataggio periodico e al logout

## 📊 Ottimizzazioni Performance

### Architettura a Tre Livelli

```
┌─────────────────────────────────────┐
│   Cache In-Memory (ConcurrentMap)   │  ← Accesso istantaneo
├─────────────────────────────────────┤
│   Connection Pool (HikariCP x20)    │  ← Pool gestito
├─────────────────────────────────────┤
│   Database H2 (File-Based)          │  ← Persistenza
└─────────────────────────────────────┘
```

### Strategia di Salvataggio

| Evento | Quando | Dati Persi in Crash |
|--------|--------|---------------------|
| **Player Logout** | Immediato | 0 |
| **Server Shutdown** | Immediato | 0 |
| **Auto-Save** | Ogni 5 minuti | Max 5 minuti |

**Perché 5 minuti?**
- Con 500 giocatori e 1 transazione/2min = ~4 write/sec
- **Salvataggio immediato**: 1200 write ogni 5 minuti
- **Salvataggio periodico**: 48 batch write ogni 5 minuti
- **Riduzione carico**: 96% in meno di operazioni I/O
- **Rischio crash improvviso**: Basso vs benefici performance

### Connection Pooling

```java
Pool HikariCP:
- Max Connections: 20
- Min Idle: 5
- Connection Timeout: 10s
- Max Lifetime: 30 minuti
```

Ottimizzato per gestire centinaia di query concorrenti senza saturare il database.

### Perché H2 invece di SQLite/MySQL?

| Database | Velocità Write | Setup | Scalabilità | Multi-Server |
|----------|---------------|-------|-------------|--------------|
| **H2** | ⭐⭐⭐⭐⭐ | Zero config | Ottima | No |
| SQLite | ⭐⭐⭐ | Zero config | Buona | No |
| MySQL | ⭐⭐⭐⭐ | Server esterno | Ottima | Sì |

H2 offre il **miglior rapporto performance/semplicità** per server singoli. È 20-30% più veloce di SQLite e non richiede server esterni.

## 📦 Installazione

### Requisiti

- **Spigot/Paper**: 1.21.8 o superiore
- **Java**: 21

### Procedura

1. Scarica il JAR da [Releases](https://github.com/marskernel/trial-economy/releases)
2. Copia in `plugins/trial-economy-1.0-SNAPSHOT.jar`
3. Riavvia il server
4. Configura `plugins/trial-economy/config.yml`
5. Ricarica con `/reload confirm` o riavvia

## ⚙️ Configurazione

### config.yml

```yaml
# Bilancio iniziale per nuovi giocatori
starting-balance: 1000.0

# Simbolo della valuta (es: $, €, ¤)
currency-symbol: "€"

# Nome della valuta (es: dollari, euro, monete)
currency-name: "euro"

# Bilancio massimo per giocatore
max-balance: 1000000000.0

# Importo minimo per transazioni /pay
min-transaction: 0.01

# Durata cache in secondi (tempo di permanenza dati offline player)
# 1800 = 30 minuti (consigliato per server grandi)
# 3600 = 1 ora (consigliato per server piccoli)
cache-duration: 1800
```

### Tuning Performance

**Per server con 100-200 giocatori:**
```yaml
cache-duration: 3600  # 1 ora
```

**Per server con 500+ giocatori:**
```yaml
cache-duration: 1800  # 30 minuti
```

La cache più lunga = migliori performance ma più RAM usata.

## 🎮 Comandi

### /balance
Mostra il tuo bilancio attuale.

**Aliases**: `/bal`, `/money`, `/soldi`

**Permesso**: `economy.balance` (default: true)

**Esempio output:**
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  IL TUO BILANCIO

  » €1,250.50 euro

━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### /pay <giocatore> <importo>
Invia denaro ad un altro giocatore (online o offline).

**Aliases**: `/invia`, `/transfer`, `/trasferisci`

**Permesso**: `economy.pay` (default: true)

**Esempi:**
```
/pay Steve 100        → Invia €100 a Steve
/pay Alex 50.50       → Invia €50.50 ad Alex
/pay Notch 1000000    → Invia €1M a Notch (se hai fondi)
```

**Validazioni:**
- ✅ Controlla fondi sufficienti
- ✅ Importo positivo e >= min-transaction
- ✅ Giocatore target esiste (anche offline)
- ✅ Non puoi pagare te stesso
- ✅ Rispetta max-balance del ricevente

## 🔐 Permessi

| Permesso | Descrizione | Default |
|----------|-------------|---------|
| `economy.*` | Tutti i permessi economy | true |
| `economy.balance` | Usa /balance | true |
| `economy.pay` | Usa /pay | true |

### Esempio LuckPerms

```bash
# Dare tutti i permessi economy a un gruppo
/lp group default permission set economy.* true

# Rimuovere /pay per un gruppo
/lp group guests permission set economy.pay false

# Permesso solo /balance per un giocatore
/lp user Steve permission set economy.balance true
/lp user Steve permission set economy.pay false
```

## 🗄️ Struttura Database

### Tabella: player_balances

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| player_uuid | VARCHAR(36) PK | UUID del giocatore |
| player_name | VARCHAR(16) | Nome del giocatore |
| balance | DECIMAL(20,2) | Bilancio corrente |
| last_updated | TIMESTAMP | Ultimo aggiornamento |

**Indici:** `idx_player_name` per ricerca rapida per nome

### Tabella: transaction_logs

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| id | INT PK AUTO | ID transazione |
| sender_uuid | VARCHAR(36) | UUID mittente |
| receiver_uuid | VARCHAR(36) | UUID destinatario |
| amount | DECIMAL(20,2) | Importo trasferito |
| transaction_type | VARCHAR(20) | Tipo (TRANSFER, ecc) |
| timestamp | TIMESTAMP | Data/ora transazione |
| description | VARCHAR(255) | Descrizione |

**Indici:** `idx_transaction_timestamp` per query temporali veloci

### Posizione File Database

```
plugins/
└── trial-economy/
    ├── config.yml
    ├── economy.mv.db      ← Database H2
    └── economy.trace.db   ← Log H2 (opzionale)
```

## 🔧 Dettagli Tecnici

### Ciclo di Vita del Bilancio

1. **Player Join** → Carica da DB in cache (async)
2. **Transazione** → Aggiorna cache + salva DB (async)
3. **Auto-Save** → Flush cache → DB ogni 5 minuti
4. **Player Quit** → Salva DB immediato + cache timer (5 min)
5. **Server Shutdown** → Salva tutti i bilanciamenti + chiudi pool

### Thread Safety

```java
// Cache thread-safe
ConcurrentHashMap<UUID, BigDecimal> balanceCache;

// Tutte le operazioni async
CompletableFuture<BigDecimal> getBalance(...);
CompletableFuture<Boolean> setBalance(...);
CompletableFuture<TransactionResult> transfer(...);
```

### Gestione Errori

- **Database Connection Fail** → Retry automatico HikariCP
- **Transazione Fallita** → Rollback automatico
- **Player Non Trovato** → Messaggio chiaro all'utente
- **Fondi Insufficienti** → Transazione bloccata

### Operazioni Atomiche

Le transazioni `/pay` sono atomiche con rollback:

```java
1. Verifica fondi sender
2. Rimuovi da sender
3. Se fallisce → STOP
4. Aggiungi a receiver
5. Se fallisce → ROLLBACK (riaccredita sender)
6. Log transazione
```

## 📈 Monitoraggio Performance

### Log di Avvio

```
=========================================
  Trial Economy Plugin
  Sistema Economy ad Alte Prestazioni
  Versione: 1.0-SNAPSHOT
=========================================
Configurazione caricata!
✓ Database H2 inizializzato con successo!
✓ EconomyManager inizializzato!
✓ Comandi registrati: /balance, /pay
✓ Event listeners registrati!
=========================================
  Trial Economy abilitato con successo!
  Tempo di caricamento: 156ms
  Giocatori online: 0
=========================================
```

### Metriche da Monitorare

- **Tempo caricamento**: < 200ms (ottimo)
- **Salvataggio cache**: Log ogni 5 minuti
- **Errori DB**: Verificare nei log per retry

## 🐛 Troubleshooting

### Plugin non si carica

**Errore**: `NoClassDefFoundError: com/zaxxer/hikari/HikariConfig`

**Soluzione**: Usa il JAR compilato con `./gradlew shadowJar`, non il JAR normale

---

### Bilanciamenti non salvati

**Causa**: Crash del server prima dell'auto-save

**Soluzione**: Riduci `cache-duration` o configura backup DB automatici

---

### Performance lente

**Sintomi**: Lag durante `/pay`

**Soluzioni**:
1. Verifica log per errori DB
2. Aumenta `cache-duration` per ridurre query
3. Controlla I/O disco del server
4. Valuta migrazione a MySQL per multi-server

---

### Giocatore non trovato

**Errore**: "Giocatore 'X' non trovato!"

**Causa**: Giocatore non si è mai connesso al server

**Nota**: Il plugin supporta offline player solo se si sono connessi almeno una volta

## 📝 Build dal Sorgente

```bash
# Clone repository
git clone https://github.com/marskernel/trial-economy.git
cd trial-economy

# Compila con Shadow
./gradlew clean shadowJar

# JAR finale in:
# build/libs/trial-economy-1.0-SNAPSHOT.jar
```

---

**Sviluppato con ❤️ per il trial di Nantex25Studios**
