import { useState } from 'react';
import { Text } from 'react-native';
import { ApiError } from '@/api';
import { reviewsApi, validateReview, MAX_REVIEW_TEXT, type ReviewableItem } from '@/features/reviews/api';
import { useAsync } from '@/features/useAsync';
import { AsyncView, Button, Card, ErrorBanner, Field, Muted, Screen, Title } from '@/components/ui';
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
    catch (e) { setError(e instanceof ApiError ? e.message : 'Yorum gonderilemedi'); }
    finally { setBusy(false); }
  }
  return (
    <Card>
      <Text style={{ fontWeight: '700' }}>{item.hotelName}</Text>
      <Muted>{item.description}</Muted>
      <StarPicker value={score} onChange={setScore} />
      <Field label="Yorumunuz (istege bagli)" value={text} onChangeText={setText} multiline maxLength={MAX_REVIEW_TEXT} autoCapitalize="sentences" />
      <ErrorBanner message={error} />
      <Button title={busy ? 'Gonderiliyor...' : 'Yorumu gonder'} disabled={busy} onPress={submit} />
    </Card>
  );
}

export default function Reviews() {
  const state = useAsync(() => reviewsApi.reviewable(), []);
  return (
    <Screen>
      <Title>Yorum yazabilecegim konaklamalar</Title>
      <AsyncView<ReviewableItem[]> state={state} isEmpty={(d) => d.length === 0} emptyText="Yorum yazilacak konaklama yok.">
        {(list) => list.map((i) => <ReviewForm key={i.orderItemId} item={i} onDone={state.reload} />)}
      </AsyncView>
    </Screen>
  );
}
