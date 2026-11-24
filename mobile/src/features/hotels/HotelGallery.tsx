import { useState } from 'react';
import { FlatList, Text, View } from 'react-native';
import { Image } from 'expo-image';
import { API_URL } from '@/config';
import { photoAspect, photoUrl, type HotelPhoto } from './api';

/** Paged photo gallery. Renders nothing when there are no photos. */
export function HotelGallery({ hotelId, photos }: { hotelId: string; photos: HotelPhoto[] }) {
  const [width, setWidth] = useState(0);
  const [index, setIndex] = useState(0);
  if (photos.length === 0) return null;
  return (
    <View onLayout={(e) => setWidth(e.nativeEvent.layout.width)}>
      {width > 0 ? (
        <FlatList
          data={photos}
          horizontal
          pagingEnabled
          showsHorizontalScrollIndicator={false}
          keyExtractor={(p) => p.id}
          onMomentumScrollEnd={(e) => setIndex(Math.round(e.nativeEvent.contentOffset.x / width))}
          renderItem={({ item }) => (
            <Image
              source={{ uri: photoUrl(API_URL, hotelId, item.id) }}
              accessibilityLabel={item.caption ?? undefined}
              contentFit="cover"
              style={{ width, aspectRatio: photoAspect(item.width, item.height), borderRadius: 8 }}
            />
          )}
        />
      ) : null}
      <Text style={{ textAlign: 'center' }}>{`${index + 1} / ${photos.length}`}</Text>
    </View>
  );
}
