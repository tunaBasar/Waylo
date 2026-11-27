import { useState } from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { Image } from 'expo-image';
import { API_URL } from '@/config';
import { PhotoFallback } from '@/components/PhotoFallback';
import { color, radius, space, type } from '@/theme/tokens';
import { photoAspect, photoUrl, type HotelPhoto } from './api';

function Slide({ hotelId, photo, width }: { hotelId: string; photo: HotelPhoto; width: number }) {
  const [failed, setFailed] = useState(false);
  const aspect = photoAspect(photo.width, photo.height);
  if (failed) return <PhotoFallback style={{ width, aspectRatio: aspect }} />;
  return (
    <Image
      source={{ uri: photoUrl(API_URL, hotelId, photo.id) }}
      accessibilityLabel={photo.caption ?? undefined}
      contentFit="cover"
      onError={() => setFailed(true)}
      style={{ width, aspectRatio: aspect }}
    />
  );
}

/** Paged photo gallery. Renders nothing when there are no photos. */
export function HotelGallery({ hotelId, photos }: { hotelId: string; photos: HotelPhoto[] }) {
  const [width, setWidth] = useState(0);
  const [index, setIndex] = useState(0);
  if (photos.length === 0) return null;
  return (
    <View onLayout={(e) => setWidth(e.nativeEvent.layout.width)} style={s.box}>
      {width > 0 ? (
        <FlatList
          data={photos}
          horizontal
          pagingEnabled
          showsHorizontalScrollIndicator={false}
          keyExtractor={(p) => p.id}
          onMomentumScrollEnd={(e) => setIndex(Math.round(e.nativeEvent.contentOffset.x / width))}
          renderItem={({ item }) => <Slide hotelId={hotelId} photo={item} width={width} />}
        />
      ) : null}
      <View accessible accessibilityLabel={`Fotoğraf ${index + 1} / ${photos.length}`} style={s.counter}>
        <Text style={[type.monoSm, { color: color.onPrimary }]}>{`${index + 1} / ${photos.length}`}</Text>
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  box: { borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.surfaceContainer },
  counter: { position: 'absolute', right: space.md, bottom: space.md, backgroundColor: 'rgba(25,28,32,0.72)', borderRadius: radius.full, paddingHorizontal: space.sm, paddingVertical: space.xs },
});
