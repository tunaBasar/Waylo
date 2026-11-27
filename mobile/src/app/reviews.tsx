import { useState } from 'react';
import { Text } from 'react-native';
import { ApiError } from '@/api';
import { reviewsApi, reviewErrorMessage, REVIEW_WHEN_HINT, validateReview, MAX_REVIEW_TEXT, type ReviewableItem } from '@/features/reviews/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Button, ErrorBanner, Field, Screen } from '@/components/ui';
import { accountStyles, Hint, Section } from '@/features/account/AccountUi';
import { StarPicker } from '@/components/StarPicker';

function ReviewForm({ item, onDone }: { item: ReviewableItem; onDone: () => void }) {
  const [score, setScore] = useState(0);
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function submit() {
    const err = validateReview(score, text);
    if (err) { setError(err); return; }
    setBusy(true); setError(null);
    try { await reviewsApi.create(item.orderItemId, score, text); onDone(); }
    catch (e) { setError(e instanceof ApiError ? reviewErrorMessage(e) : 'Yorum gönderilemedi'); }
    finally { setBusy(false); }
  }
  return (
    <Section>
      <Text style={accountStyles.strong}>{item.hotelName}</Text>
      <Hint>{item.description}</Hint>
      <StarPicker value={score} onChange={setScore} />
      <Field label="Yorumunuz (istege bagli)" value={text} onChangeText={setText} multiline maxLength={MAX_REVIEW_TEXT} autoCapitalize="sentences" />
      <ErrorBanner message={error} />
      <Button title={busy ? 'Gonderiliyor...' : 'Yorumu gonder'} disabled={busy} onPress={submit} />
    </Section>
  );
}

export default function Reviews() {
  const state = useAsync(() => reviewsApi.reviewable(), []);
  return (
    <Screen>
      <Hint>{REVIEW_WHEN_HINT}</Hint>
      <AsyncView<ReviewableItem[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Yorum yazilacak konaklama yok.">
        {(list) => list.map((i) => <ReviewForm key={i.orderItemId} item={i} onDone={state.reload} />)}
      </AsyncView>
    </Screen>
  );
}
