'use strict';

var path = require("path");
var webpack = require('webpack');

const CleanWebPackPlugin = require('clean-webpack-plugin');

module.exports = {

  entry: {
    index: './bundles/index.js',
    mgmtUi: './target/blended-mgmt-ui-opt.js'
  },

  output: {
    path: path.resolve(__dirname, 'target/assets'),
    publicPath: "/assets/",
    filename: '[name]-bundle.js'
  },

  devtool: "source-map",

  devServer: {
    port: 8090,
    clientLogLevel: "info",
    proxy: {
      "/management": {
        target: "http://localhost:8090",
        pathRewrite: {"^/management": ""}
      }
    }
  },

  plugins: [
    new CleanWebPackPlugin('target/assets'),
    new webpack.NoEmitOnErrorsPlugin(),
    new webpack.ProvidePlugin({
      $: 'jquery',
      jQuery: 'jquery'
    })
  ],

  module: {
    loaders: [
      {
        test: /\.css$/,
        use: [
          {
            loader: 'style-loader'
          },
          {
            loader: 'css-loader'
          }
        ]
      },

      {
        test: /\.less$/,
        use: [{
          loader: "style-loader" // creates style nodes from JS strings
        }, {
          loader: "css-loader" // translates CSS into CommonJS
        }, {
          loader: "less-loader" // compiles Less to CSS
        }]
      },

      {
        test: /\.woff2?$|\.ttf$|\.eot$|\.svg$/,
        use: [{
          loader: "file-loader"
        }]
      },

      {
        test: /\.(png|jpg|svg)$/,
        use: [
          {
            loader: 'url-loader',
            options: {
              query: {
                limit: '8192'
              }
            }
          },
          {
            loader: 'image-webpack-loader',
            options: {
              query: {
                mozjpeg: {
                  progressive: true
                },
                gifsicle: {
                  interlaced: true
                },
                optipng: {
                  optimizationLevel: 7
                }
              }
            }
          }
        ]
      }
    ]
  }
};