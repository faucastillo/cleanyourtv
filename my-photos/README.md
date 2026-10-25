# my-photos

The pictures for the photo card on the glass Home (`glass_home.photo_card` in a theme). Put your own photos here
(JPEG, PNG or WebP; any size, any file name) and send them to the TV:

```sh
TV_HOST=<tv ip> tools/photos.sh            # resizes, fixes rotation, replaces the photos on the TV
TV_HOST=<tv ip> tools/photos.sh --clear    # removes them from the TV
```

Everything in this folder except this README and `sample.jpg` is git-ignored: the fork is public, your photos stay
on this computer and on the TV. Delete `sample.jpg` once you have added your own.

`sample.jpg` is Península Llao Llao, Bariloche, by Fernando, CC BY-SA 4.0
([Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Llao_Llao_Peninsula.jpg)).
