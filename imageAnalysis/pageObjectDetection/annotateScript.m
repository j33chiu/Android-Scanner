%% load/update images
clear;
% does file already exist
if exist('raw_data', 'file')
    loaded = readcell('raw_data');
    already_exists = length(loaded);
else
    cd raw_images;

    images_to_load = dir('*.jpg');
    nfiles = length(images_to_load);
    already_exists = 0;
    loaded = cell(1, nfiles);
end

for i = 1:nfiles
    currentFile = images_to_load(i).name;
    currentImage = imread(currentFile);
    %gray = rgb2gray(currentImage);
    loaded{1, i + already_exists} = currentImage;
end

cd ..

writecell(loaded, 'raw_data');