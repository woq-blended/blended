'use strict';

var webpack = require('webpack');
var _ = require('lodash');

module.exports = _.merge(
  require('./scalajs.webpack.config'),
  require('./webpack.config.shared'),
  {

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
      new webpack.DefinePlugin({
        'process.env.NODE_ENV': JSON.stringify('development')
      })
    ],
  });
