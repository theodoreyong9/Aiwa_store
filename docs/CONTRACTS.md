# Writing a contract

A contract here is not code that a machine runs for everyone. It is a set of rules, **state + event → new state**, that each holder
replays from the events they have. Two holders with the same events get the same state ([yellow paper §16](YELLOWPAPER.md)).
The SDK is one module, `lib/aiwa.js`, which an app imports by its address.

## The smallest one: a vote

The whole app, which runs in the Store, is [`aiwa-app-example.html`](aiwa-app-example.html). Its three parts:

```js
import { generateIdentity, EventLog, createMemoryBackend, defineContract, Contract, signedAction, verifySignedAction }
  from 'https://theodoreyong9.github.io/Aiwa_store/lib/aiwa.js';

// 1. The rules: what each kind of event does to the state.
const definition = defineContract({
  initialState: () => ({ votes: {} }),
  handlers: {
    vote: async (state, event) => {
      const action = event.payload;
      if (!(await verifySignedAction(action))) return state;       // not signed by who it says it is
      if (action.choice !== 'yes' && action.choice !== 'no') return state;
      if (action.from in state.votes) return state;                // one vote per identity: the first counts
      return { votes: { ...state.votes, [action.from]: action.choice } };
    },
  },
});

// 2. A log of events, and a handle on the contract.
const me = await generateIdentity();
const contract = new Contract({ identity: me, log: new EventLog(createMemoryBackend()), domain: 'show-of-hands', definition });

// 3. Act, and read.
await contract.dispatch('vote', await signedAction(me, { from: me.id, choice: 'yes' }));
const { votes } = await contract.state();
```

## Three rules

1. **Prove who acts inside the action.** A handler never sees who published an event, and a plain `payload.from` proves nothing: anyone
   can write anyone's name in it. `signedAction` signs the fields, `verifySignedAction` checks them. A handler that skips it can be made
   to count an action "from" anyone (§16.2).
2. **Keep handlers pure.** Every reader replays them. The same events must give the same state, so no clock, no random number and no
   network call inside a handler. Nothing enforces this: it is on you.
3. **A conflict has one winner, the same for everyone.** Two votes by one identity, one coin spent twice: readers fold the events in
   a fixed order, so they all pick the same one (§13.4). Write the rule so that the first counts, as above.

## Keeping the state between two openings

An app has no storage of its own (its frame has an opaque origin). The Store lends each app a **reserve of its own** through the door, and the SDK
speaks to it, so that the journal and the player survive the frame:

```js
import { EventLog, Contract, defineContract, createDoorBackend, loadOrCreateIdentity, doorStorage }
  from 'https://theodoreyong9.github.io/Aiwa_store/lib/aiwa.js';

const me = await loadOrCreateIdentity();                      // the same player at each opening
const log = new EventLog(createDoorBackend());                // the events outlive the frame
const contract = new Contract({ identity: me, log, domain: 'show-of-hands', definition });
await doorStorage.set('best', '12');                          // strings, short keys
```

The reserve is the app's alone (never another app's, never the wallet's) and bounded: a key of up to 100 characters, a value of 256 KB, 2000 keys,
2 MB in all. Outside the Store the door does not answer and everything falls back to memory: the app still runs, it only forgets when it closes. A reserve is
on this phone only: to share events between players, see below.

## Sharing events between phones

The example does not do this: its log is in memory (or, with `createDoorBackend()`, on this phone), so **a vote counts only on the phone that cast it**. Events are plain data. They
travel as text, by a QR code, a paste or a data channel (the [click duel](demo-apps/click-duel.html) links two phones with two codes):

```js
import { collectAncestors, encodeOfflineBundle, decodeOfflineBundle } from '…/lib/aiwa.js';

// the events this device holds, as text
const text = encodeOfflineBundle({ events: await collectAncestors(contract.log, await contract.log.head()) });
// … carry `text` to the other phone, which takes them in:
await otherContract.log.appendMany(decodeOfflineBundle(text).events);
```

Once both have each other's events they hold the same state. Until then each folds only what it holds, and two holders with different
events may differ (§13). Live replication over a transport exists in the platform layer (`aiwa-platform`'s `Replicator`), but it is not
in `lib/aiwa.js`.

## Publishing it

In the widget, the **Aiwa** kind (the *Aiwa* button) makes Claude write exactly this kind of file, named `name.aiwa.html`, **after its test**
(`name.aiwa.test.mjs`, run with `node`: signed events in, expected state and refusals out, a replay gives the same state). Claude delivers a **draft** into the
author's library and publishes nothing. The author then publishes it (the **⇪** of *Mes apps*, or the publish sheet ▦), through Aiwa: the registry keeps two files per version, a package that is only a pointer (the number of a signed manifest) and the signed bundle that carries the files, every one pinned by its
hash ([docs/ANDROID.md](ANDROID.md)).

A file written by hand, such as the example above, is published from the Store itself: press **＋**, paste the file, and the same sheet follows
(a file that imports `lib/aiwa.js` is taken for a contract). Publishing needs a wallet that has mined: the app is ranked by what its author has
mined (score and laps), and the sheet says so when there is nothing to publish with yet.

## What it cannot do

- **Count people.** Identities in a contract are free (`generateIdentity()` costs nothing), so "one vote per identity" is not "one vote
  per person". Anything that matters needs a cost, like the wallet's identities, which cost a burn.
- **Move real AIWA.** The hook exists and is tested (§16.3), but the Store ships no contract registered into it. A contract's state is
  its own.
- **Reach the wallet** unless the app says so: the sandbox gives it nothing, and a banner shows when it asks ([docs/EXPLAINED.md](EXPLAINED.md)).

The snippets above run as written in `packages/lib/test/contract-guide.test.mjs`: two devices exchanging events, one identity voting
twice, a forged action, and many free identities.
